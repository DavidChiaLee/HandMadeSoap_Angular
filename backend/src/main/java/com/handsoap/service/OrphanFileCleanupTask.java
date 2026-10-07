package com.handsoap.service;

import com.handsoap.repository.EssentialOilImageRepository;
import com.handsoap.repository.OilImageRepository;
import com.handsoap.repository.SoapImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 孤兒圖檔排程掃描（review M2 第三點）。
 *
 * <p>定位：這是<b>第二道網</b>，不是主要的清理機制。主要機制是刪除 entity／單圖時
 * 由 {@link FileStorageService#deleteFilesAfterCommit} 在 commit 後清檔；
 * 本排程負責回收那些路徑漏掉的檔案：
 * <ul>
 *   <li>本功能落地<b>之前</b>歷史刪除累積的孤兒檔（DB 列早已被 CASCADE 掉）；</li>
 *   <li>commit 成功但 {@code deleteFile} 失敗留下的殘檔（IO 錯誤、權限問題）；</li>
 *   <li>上傳流程中「檔案已寫入、但 DB commit 失敗」產生的孤兒
 *       （saveFile 先落盤、transaction 後 commit，中間失敗就沒有任何 DB 引用）。</li>
 * </ul>
 *
 * <p><b>安全設計（缺一不可）：</b>
 * <ol>
 *   <li><b>寬限期</b>：只處理 {@code lastModified} 早於 {@code min-age-hours} 的檔案。
 *       上傳流程是「先寫檔、後 commit」，剛落盤的檔案在引用查詢中還查不到，
 *       沒有寬限期就會把進行中的上傳當孤兒刪掉 —— 這是本排程最大的風險。</li>
 *   <li><b>引用集合</b>：涵蓋 soap_images / oil_images / essential_oil_images 三張表
 *       （漏掉任何一張就會誤刪該表引用的活檔）。</li>
 *   <li><b>副檔名白名單</b>：只碰 {@code jpg/jpeg/png/webp/gif}，
 *       上傳目錄內手動放入的其他檔案（備份、暫存檔）不受影響。</li>
 *   <li><b>逐檔容錯</b>：單檔失敗只記 log 繼續，不中斷整批掃描。</li>
 * </ol>
 *
 * <p><b>單實例前提</b>：docker-compose.yml 只有一個 backend replica，
 * 兩個執行個體同時掃描只會重複刪同一批檔案（deleteFile 對不存在的檔靜默成功），
 * 因此不需要分散式鎖。日後若水平擴展 backend，需改用 ShedLock 之類的分散式排他。
 *
 * <p>停用方式：設定 {@code app.orphan-cleanup.enabled=false} 後重啟
 * （{@code @ConditionalOnProperty} 使本 Bean 根本不載入，非僅跳過執行）。
 */
@Component
@ConditionalOnProperty(name = "app.orphan-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class OrphanFileCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(OrphanFileCleanupTask.class);

    private final FileStorageService fileStorageService;
    private final SoapImageRepository soapImageRepository;
    private final OilImageRepository oilImageRepository;
    private final EssentialOilImageRepository essentialOilImageRepository;

    /** 寬限期：距今小於此小時數的檔案一律不動。 */
    private final long minAgeHours;

    public OrphanFileCleanupTask(FileStorageService fileStorageService,
                                 SoapImageRepository soapImageRepository,
                                 OilImageRepository oilImageRepository,
                                 EssentialOilImageRepository essentialOilImageRepository,
                                 @Value("${app.orphan-cleanup.min-age-hours:24}") long minAgeHours) {
        this.fileStorageService = fileStorageService;
        this.soapImageRepository = soapImageRepository;
        this.oilImageRepository = oilImageRepository;
        this.essentialOilImageRepository = essentialOilImageRepository;
        this.minAgeHours = minAgeHours;
    }

    /**
     * 每日凌晨掃一次（預設 03:30，可由 {@code app.orphan-cleanup.cron} 覆寫）。
     * 選低流量時段是為了避免與管理員的上傳操作撞期 —— 雖有寬限期保護，
     * 但低流量時段連「檔案較舊、正在編輯中」這類邊緣情境都更不可能發生。
     */
    @Scheduled(cron = "${app.orphan-cleanup.cron:0 30 3 * * *}")
    public void sweep() {
        int deleted = sweepOnce();
        if (deleted > 0) {
            log.info("[orphan-cleanup] 掃描完成，已刪除 {} 個孤兒圖檔", deleted);
        }
    }

    /**
     * 一次掃描的本體（package-private 供單測直接呼叫，迴避排程與 cron）。
     *
     * @return 成功刪除的檔案數；引用查詢失敗時直接往外拋（fail-closed：絕不在
     *         不確定引用集合的情況下刪檔）
     */
    int sweepOnce() {
        Set<String> referenced = loadReferencedPaths();
        Instant cutoff = Instant.now().minus(minAgeHours, ChronoUnit.HOURS);
        int deleted = 0;

        for (String filename : fileStorageService.listRootFilenames()) {
            if (referenced.contains(filename)) {
                continue;
            }
            if (!fileStorageService.hasAllowedExtension(filename)) {
                continue;
            }
            Instant lastModified = fileStorageService.lastModified(filename);
            if (lastModified == null || lastModified.isAfter(cutoff)) {
                // null = 掃描期間已被別人刪掉；isAfter = 寬限期內（可能正在上傳）
                continue;
            }
            try {
                fileStorageService.deleteFile(filename);
                deleted++;
                log.info("[orphan-cleanup] 已刪除孤兒圖檔：{}", filename);
            } catch (RuntimeException e) {
                // 單檔失敗不中斷整批；殘檔留待下次掃描
                log.warn("[orphan-cleanup] 刪除孤兒圖檔失敗：{} 原因={}", filename, e.getMessage());
            }
        }

        logReverseOrphans(referenced);
        return deleted;
    }

    /** 三張圖片表的全部被引用檔名（去空白、去 null、去重）。 */
    private Set<String> loadReferencedPaths() {
        Set<String> referenced = new HashSet<>();
        soapImageRepository.findAllImagePaths().forEach(referenced::add);
        oilImageRepository.findAllImagePaths().forEach(referenced::add);
        essentialOilImageRepository.findAllImagePaths().forEach(referenced::add);
        referenced.removeIf(Objects::isNull);
        referenced.removeIf(String::isBlank);
        return referenced;
    }

    /**
     * 反向孤兒：DB 有引用但磁碟沒有對應檔案（引用值穿越／手動刪檔／備份還原不同步）。
     * 只記 log 不動作 —— 修復需要人工判斷（補檔 or 修 DB），自動化反而危險。
     */
    private void logReverseOrphans(Set<String> referenced) {
        for (String path : referenced) {
            try {
                if (fileStorageService.lastModified(path) == null) {
                    log.warn("[orphan-cleanup] DB 引用的圖檔在磁碟上不存在（前台將顯示破圖）：{}", path);
                }
            } catch (RuntimeException e) {
                log.warn("[orphan-cleanup] 檢查引用檔案失敗：{} 原因={}", path, e.getMessage());
            }
        }
    }
}
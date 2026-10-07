package com.handsoap.service;

import com.handsoap.config.FileUploadConfig;
import com.handsoap.repository.EssentialOilImageRepository;
import com.handsoap.repository.OilImageRepository;
import com.handsoap.repository.SoapImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * {@link OrphanFileCleanupTask} 掃描邏輯測試。
 *
 * <p>以真實的 {@link FileStorageService} + {@code @TempDir} 驗證檔案系統行為
 * （寬限期、白名單、引用集合），repository 則以 mock 提供引用清單。
 */
@ExtendWith(MockitoExtension.class)
class OrphanFileCleanupTaskTest {

    @Mock
    private FileUploadConfig fileUploadConfig;

    @Mock
    private SoapImageRepository soapImageRepository;

    @Mock
    private OilImageRepository oilImageRepository;

    @Mock
    private EssentialOilImageRepository essentialOilImageRepository;

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    /** 寬限期 24 小時，與 application.properties 預設一致。 */
    private OrphanFileCleanupTask task;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(fileUploadConfig);
        task = new OrphanFileCleanupTask(
                fileStorageService, soapImageRepository, oilImageRepository,
                essentialOilImageRepository, 24);
    }

    private void stubEmptyReferences() {
        given(soapImageRepository.findAllImagePaths()).willReturn(List.of());
        given(oilImageRepository.findAllImagePaths()).willReturn(List.of());
        given(essentialOilImageRepository.findAllImagePaths()).willReturn(List.of());
    }

    /** 建立檔案並把 lastModified 設為 hoursAgo 小時前。 */
    private Path createFile(String name, long hoursAgo) throws IOException {
        Path file = tempDir.resolve(name);
        Files.createFile(file);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(hoursAgo, ChronoUnit.HOURS)));
        return file;
    }

    @Test
    @DisplayName("過期且未被引用的白名單圖檔應被刪除")
    void shouldDeleteExpiredUnreferencedWhitelistedFile() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        stubEmptyReferences();
        Path orphan = createFile("orphan.png", 48);

        int deleted = task.sweepOnce();

        assertThat(deleted).isEqualTo(1);
        assertThat(orphan).doesNotExist();
    }

    @Test
    @DisplayName("過期但被 soap_images 引用的檔案應保留")
    void shouldKeepExpiredFileReferencedBySoapImages() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        given(soapImageRepository.findAllImagePaths()).willReturn(List.of("used.png"));
        given(oilImageRepository.findAllImagePaths()).willReturn(List.of());
        given(essentialOilImageRepository.findAllImagePaths()).willReturn(List.of());
        Path used = createFile("used.png", 48);

        int deleted = task.sweepOnce();

        assertThat(deleted).isZero();
        assertThat(used).exists();
    }

    @Test
    @DisplayName("過期但被 oil_images / essential_oil_images 引用的檔案也應保留（引用集合須涵蓋三表）")
    void shouldKeepExpiredFileReferencedByOtherImageTables() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        given(soapImageRepository.findAllImagePaths()).willReturn(List.of());
        given(oilImageRepository.findAllImagePaths()).willReturn(List.of("oil-ref.png"));
        given(essentialOilImageRepository.findAllImagePaths()).willReturn(List.of("eo-ref.png"));
        Path oilRef = createFile("oil-ref.png", 72);
        Path eoRef = createFile("eo-ref.png", 72);

        int deleted = task.sweepOnce();

        assertThat(deleted).isZero();
        assertThat(oilRef).exists();
        assertThat(eoRef).exists();
    }

    @Test
    @DisplayName("寬限期內的新檔即使未被引用也不可刪（防刪到上傳中的活檔）")
    void shouldKeepRecentFileWithinGracePeriod() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        stubEmptyReferences();
        Path fresh = createFile("fresh-upload.png", 1);

        int deleted = task.sweepOnce();

        assertThat(deleted).isZero();
        assertThat(fresh).exists();
    }

    @Test
    @DisplayName("白名單外的副檔名一律不碰")
    void shouldSkipFilesOutsideExtensionWhitelist() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        stubEmptyReferences();
        Path svg = createFile("manual-backup.svg", 48);
        Path noExt = createFile("README", 48);

        int deleted = task.sweepOnce();

        assertThat(deleted).isZero();
        assertThat(svg).exists();
        assertThat(noExt).exists();
    }

    @Test
    @DisplayName("混合情況：只刪「過期 + 未引用 + 白名單」交集，其餘全保留")
    void shouldDeleteOnlyIntersectionOfAllConditions() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        given(soapImageRepository.findAllImagePaths()).willReturn(List.of("referenced-old.png"));
        given(oilImageRepository.findAllImagePaths()).willReturn(List.of());
        given(essentialOilImageRepository.findAllImagePaths()).willReturn(List.of());

        Path orphanOld = createFile("orphan-old.png", 48);
        Path referencedOld = createFile("referenced-old.png", 48);
        Path orphanNew = createFile("orphan-new.png", 1);
        Path orphanOldSvg = createFile("orphan-old.svg", 48);

        int deleted = task.sweepOnce();

        assertThat(deleted).isEqualTo(1);
        assertThat(orphanOld).doesNotExist();
        assertThat(referencedOld).exists();
        assertThat(orphanNew).exists();
        assertThat(orphanOldSvg).exists();
    }

    @Test
    @DisplayName("引用清單含空白/null 值時應被過濾且不影響掃描")
    void shouldIgnoreBlankAndNullReferencedPaths() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        given(soapImageRepository.findAllImagePaths()).willReturn(java.util.Arrays.asList("", null));
        given(oilImageRepository.findAllImagePaths()).willReturn(List.of("  "));
        given(essentialOilImageRepository.findAllImagePaths()).willReturn(List.of());
        Path orphan = createFile("still-orphan.png", 48);

        int deleted = task.sweepOnce();

        assertThat(deleted).isEqualTo(1);
        assertThat(orphan).doesNotExist();
    }

    @Test
    @DisplayName("上傳目錄不存在時掃描應回 0 且不拋例外")
    void shouldReturnZeroWhenUploadDirMissing() {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.resolve("nope").toString());
        stubEmptyReferences();

        assertThat(task.sweepOnce()).isZero();
    }
}
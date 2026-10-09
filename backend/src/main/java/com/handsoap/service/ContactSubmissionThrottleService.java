package com.handsoap.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 聯絡表單的每 IP 滑動窗口限流（review 728）。
 *
 * <h2>為什麼是「滑動窗口」而不是固定窗口</h2>
 *
 * <p>固定窗口（例如整點起算 5 件）在窗口交界處可瞬間打出 10 件
 * （前一秒 5 件 + 後一秒 5 件），等於門檻形同虛設。滑動窗口以
 * 「最近 N 分鐘內的成功次數」計算，無交界漏洞。</p>
 *
 * <h2>為什麼只計「成功寫入」</h2>
 *
 * <p>只在 {@link #recordSuccess(String)}（確實存進資料庫）時計數：
 * honeypot 觸發、欄位驗證失敗、內容重複都不計。若把失敗請求也計入，
 * 攻擊者可以灌垃圾請求墊高（污染）真人 IP 的計數 ——
 * 這正是 review H5 討論 {@code LoginAttemptService} 時記錄過的反面教材
 * （DoS 自己的合法使用者）。</p>
 *
 * <h2>狀態儲存</h2>
 *
 * <p>同 {@code LoginAttemptService}：記憶體 {@link ConcurrentHashMap}，
 * 單一 backend 實例足夠；重啟清空計數可接受。多實例部署時
 * <b>必須</b>改為 Redis，否則實際上限會變成 N 倍。</p>
 *
 * <p>與 nginx {@code limit_req} 的分工：nginx 擋「洪水式」大量請求（r/m 級），
 * 本服務擋「慢速但持續」的灌入（每小時 5 件這種 nginx 無法表達的語意，
 * limit_req 只有 r/s 與 r/m）。兩層缺一不可。</p>
 */
@Service
public class ContactSubmissionThrottleService {

    private static final Logger log = LoggerFactory.getLogger(ContactSubmissionThrottleService.class);

    private final int maxPerWindow;
    private final Duration window;

    /**
     * 時鐘來源。刻意<b>非</b> {@code final}：{@link #forTesting} 會覆寫成測試用的
     * 假時鐘。實務上建構後不再變更；執行緒安全由「僅單一測試在建構期寫入、
     * 之後只讀」保證（正式路徑永不覆寫）。
     */
    private LongSupplier clock;

    /** key = 用戶端 IP；value = 該 IP 最近成功送出的時間戳（由舊至新）。 */
    private final Map<String, Deque<Long>> submissions = new ConcurrentHashMap<>();

    /**
     * <b>全專案唯一的建構子</b>（勿加第二個，見 forTesting 註解的事故說明）。
     */
    public ContactSubmissionThrottleService(
            @Value("${app.contact.max-per-window:5}") int maxPerWindow,
            @Value("${app.contact.window-minutes:60}") long windowMinutes) {
        this.maxPerWindow = maxPerWindow;
        this.window = Duration.ofMinutes(windowMinutes);
        this.clock = System::currentTimeMillis;
    }

    /**
     * 測試專用入口：注入 clock 以便驗證窗口滑動，不必真的 sleep。
     *
     * <p><b>為什麼是靜態工厂而不是第二個建構子</b>：Spring 只在「類別只有一個
     * 建構子」時才會自動挑選它；一旦有第二個（哪怕 package-private），Spring
     * 會改找無參建構子並拋 {@code No default constructor found} →
     * 容器啟動失敗、healthcheck 永不 healthy、依賴它的 nginx 連鎖無法啟動
     * （本專案曾實際發生，單元測試全綠也擋不住 —— 因為 @MockBean 從不實例化
     * 真實 bean）。要加第二個建構子前請三思，或至少補 @SpringBootTest 煙霧測試。</p>
     */
    static ContactSubmissionThrottleService forTesting(int maxPerWindow, Duration window, LongSupplier clock) {
        ContactSubmissionThrottleService service =
                new ContactSubmissionThrottleService(maxPerWindow, window.toMinutes());
        service.clock = clock;
        return service;
    }

    /**
     * 該 IP 目前是否被限流。
     *
     * <p>回傳 {@link Duration#ZERO} 表示放行；否則回<b>剩餘封鎖時間</b>，
     * 供 {@code Retry-After} 標頭與錯誤訊息使用。
     * 刻意不回傳剩餘次數 —— 那等於把門檻直接告訴灌水者。</p>
     *
     * <p>本方法<b>只檢查不計數</b>：計數由 {@link #recordSuccess(String)}
     * 在確定寫入成功後進行，見類別註解「為什麼只計成功寫入」。</p>
     */
    public Duration check(String clientIp) {
        long now = clock.getAsLong();
        Deque<Long> queue = submissions.get(keyFor(clientIp));
        if (queue == null) {
            return Duration.ZERO;
        }
        synchronized (queue) {
            purge(queue, now);
            if (queue.size() < maxPerWindow) {
                return Duration.ZERO;
            }
            // 被擋時回「最舊一筆滿窗口」還要多久 —— 滑動窗口下每個請求的
            // 解禁時間不同，回固定值會讓使用者提早重試又撞牆。
            long remainingMillis = window.toMillis() - (now - queue.peekFirst());
            return Duration.ofMillis(Math.max(remainingMillis, 0));
        }
    }

    /** 記錄一次「成功寫入」。呼叫端必須在資料庫儲存成功後才呼叫。 */
    public void recordSuccess(String clientIp) {
        long now = clock.getAsLong();
        Deque<Long> queue = submissions.computeIfAbsent(keyFor(clientIp), k -> new ArrayDeque<>());
        synchronized (queue) {
            purge(queue, now);
            queue.addLast(now);
        }
        log.debug("[audit] contact-submitted ip={} count={}", clientIp, queue.size());
    }

    /** 清除所有計數。<b>僅供測試使用</b>，正式流程不應呼叫。 */
    void reset() {
        submissions.clear();
    }

    /** 移除已滑出窗口的時間戳，避免 deque 無限成長。呼叫端需持有 queue 鎖。 */
    private void purge(Deque<Long> queue, long now) {
        long cutoff = now - window.toMillis();
        while (!queue.isEmpty() && queue.peekFirst() < cutoff) {
            queue.removeFirst();
        }
    }

    private static String keyFor(String clientIp) {
        return clientIp == null ? "" : clientIp.trim();
    }
}
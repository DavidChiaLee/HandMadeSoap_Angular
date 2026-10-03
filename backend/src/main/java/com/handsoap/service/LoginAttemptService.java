package com.handsoap.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登入失敗次數的追蹤與漸進式封鎖（review H5）。
 *
 * <h2>為什麼用「漸進式延遲」而不是「N 次鎖 M 分鐘」</h2>
 *
 * <p>原 review 建議是「同 IP+username 5 次失敗鎖 15 分鐘」。實作時評估發現這個設計
 * 會引入比原本要解決的問題更嚴重的風險：</p>
 *
 * <ul>
 *   <li><b>按 username 鎖定 = 攻擊者可以鎖死管理員。</b>攻擊者根本不需要猜到密碼，
 *       只要知道帳號名稱（{@code admin} 是本專案的預設值，見 {@code .env.example}），
 *       打 5 次就能讓後台連續 15 分鐘無法登入，重複執行即可長期癱瘓後台。
 *       這等於把「弱密碼風險」換成「後台永久鎖死風險」，淨安全性是負的。</li>
 *   <li><b>按 IP 鎖定會誤傷共用出口的真人。</b>5 次/15 分鐘在 NAT、公司 proxy、
 *       CGNAT 行動網路下，會直接擋掉正常使用者。</li>
 * </ul>
 *
 * <p>因此改採<b>指數退避</b>：前幾次失敗不封鎖（不打擾只是打錯密碼的真人），
 * 隨著失敗次數增加，等待時間快速成長到 30 分鐘，使爆破的時間成本高到不具經濟性。
 * 成功登入會清空計數 —— 這一點同時解決了「真人被鎖死」的問題。</p>
 *
 * <h2>為什麼 username 與 IP 要分開計數</h2>
 *
 * <p>兩者防的是不同攻擊：username 擋「針對特定帳號的慢速爆破」，IP 擋「同一來源
 * 大量嘗試不同帳號」的帳號列舉。門檻刻意不同 —— IP 較寬鬆以免誤傷共用出口。</p>
 *
 * <h2>狀態儲存</h2>
 *
 * <p>使用記憶體 {@link ConcurrentHashMap}。{@code docker-compose.yml} 是單一 backend
 * 實例，此設計足夠；重啟會清空計數（可接受 —— 重啟後攻擊者也必須重新開始）。
 * 若日後改為多實例部署，<b>必須</b>改為 Redis，否則限流會形同虛設
 * （每個實例各自計數，實際上限會變成 N 倍）。</p>
 *
 * <h2>審計日誌</h2>
 *
 * <p>失敗事件一律以 {@code [audit] login-failed} 前綴寫入，便於事後 grep。
 * 注意：日誌必須搭配 {@code logging.file.name} 與 volume 掛載才能留存 ——
 * 只寫 stdout 的話，{@code docker compose down} 後全部消失，等於沒寫。</p>
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /** 首次觸發封鎖的等待時間，之後每失敗一次倍增。 */
    private static final Duration BASE_LOCK_DURATION = Duration.ofSeconds(30);

    /** 封鎖上限（30 分鐘）。再往上成長已無實質意義，只會讓紀錄失真。 */
    private static final Duration MAX_LOCK_DURATION = Duration.ofMinutes(30);

    /** 完全冷卻（無失敗紀錄）的計數會在此時機從 map 移除，避免無限成長。 */
    private static final Duration RETENTION = MAX_LOCK_DURATION.plusMinutes(30);

    /**
     * 以 username 為 key 的封鎖門檻。
     * 刻意比 IP 嚴格：攻擊者鎖定的是特定帳號，而 IP 側常是共用出口。
     */
    private final int usernameMaxAttempts;

    private final int ipMaxAttempts;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.login.username-max-attempts:5}") int usernameMaxAttempts,
            @Value("${app.login.ip-max-attempts:30}") int ipMaxAttempts) {
        this.usernameMaxAttempts = usernameMaxAttempts;
        this.ipMaxAttempts = ipMaxAttempts;
    }

    /**
     * 該 username 或該 IP 是否目前處於封鎖狀態。
     *
     * <p>任一條件成立即封鎖 —— 攻擊者可以換 IP 來繞過 username 限制，
     * 也可以換 username 來繞過 IP 限制，兩者都必須檢查。</p>
     */
    public boolean isBlocked(String username, String clientIp) {
        return remainingLock(keyFor("username", username)).compareTo(Duration.ZERO) > 0
                || remainingLock(keyFor("ip", clientIp)).compareTo(Duration.ZERO) > 0;
    }

    /**
     * 尚需等待的時間，供 {@code Retry-After} 標頭與錯誤訊息使用。
     *
     * <p>刻意<b>不</b>把剩餘次數或精確秒數回傳給使用者：說「你還能再試 2 次」
     * 等於直接告訴攻擊者門檻在哪。</p>
     */
    public Duration remainingLockDuration(String username, String clientIp) {
        Duration usernameLock = remainingLock(keyFor("username", username));
        Duration ipLock = remainingLock(keyFor("ip", clientIp));
        return usernameLock.compareTo(ipLock) >= 0 ? usernameLock : ipLock;
    }

    /** 登入失敗：同時對 username 與 IP 累加計數。 */
    public void recordFailure(String username, String clientIp) {
        int usernameFailures = increment(keyFor("username", username));
        int ipFailures = increment(keyFor("ip", clientIp));

        // 審計日誌：只記 username 與來源 IP，絕不記錄密碼或密碼長度。
        log.warn("[audit] login-failed username={} ip={} usernameFailures={} ipFailures={}",
                username, clientIp, usernameFailures, ipFailures);
    }

    /**
     * 登入成功：清空該 username 與該 IP 的計數。
     *
     * <p>這是避免真人被鎖死的關鍵 —— 打到後來終於想起密碼並成功登入時計數歸零，
     * 攻擊者先前累積的封鎖不會延續到合法使用者身上。</p>
     */
    public void recordSuccess(String username, String clientIp) {
        attempts.remove(keyFor("username", username));
        attempts.remove(keyFor("ip", clientIp));
        log.info("[audit] login-succeeded username={} ip={}", username, clientIp);
    }

    /**
     * 清除所有失敗紀錄。
     *
     * <p><b>僅供測試與營運手動重置使用</b>，正式流程不應呼叫。</p>
     */
    void reset() {
        attempts.clear();
    }

    private Duration remainingLock(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return Duration.ZERO;
        }

        long now = System.currentTimeMillis();
        if (now >= attempt.lockedUntilMillis()) {
            return Duration.ZERO;
        }

        // 已冷卻到不值得留著時趁機移除，避免 map 無限成長。
        if (now - attempt.lastFailureMillis() > RETENTION.toMillis()) {
            attempts.remove(key);
        }

        return Duration.ofMillis(attempt.lockedUntilMillis() - now);
    }

    private int increment(String key) {
        Attempt attempt = attempts.compute(key, (k, existing) -> {
            int failures = (existing == null ? 0 : existing.failures()) + 1;
            int max = k.startsWith("username:") ? usernameMaxAttempts : ipMaxAttempts;

            // 超過門檻後才套用退避；門檻之內 lockedUntil 保持 0（不封鎖）。
            long lockedUntil = failures > max
                    ? System.currentTimeMillis() + lockDurationFor(failures - max).toMillis()
                    : 0L;

            return new Attempt(failures, lockedUntil, System.currentTimeMillis());
        });
        return attempt.failures();
    }

    /**
     * 失敗次數每超出一級，等待時間倍增，並以 30 分鐘封頂。
     *
     * <p>使用位移（{@code 1L << n}）而非 {@code Math.pow}：位移在 n 較大時
     * 不會像 double 相乘那樣產生精度或溢出的問題。</p>
     */
    private Duration lockDurationFor(int overage) {
        long multiplier = 1L << Math.min(overage - 1, 20);
        Duration duration = BASE_LOCK_DURATION.multipliedBy(multiplier);
        return duration.compareTo(MAX_LOCK_DURATION) > 0 ? MAX_LOCK_DURATION : duration;
    }

    /**
     * 產生計數 key。
     *
     * <p>前綴區分 username／ip 兩組 —— 兩者門檻不同，若不小心混用同一組，
     * 會讓寬鬆的 IP 設定意外套用到嚴格的 username 規則上（或反之）。</p>
     */
    private static String keyFor(String kind, String value) {
        return kind + ":" + (value == null ? "" : value.trim());
    }

    /** 單一計數器的狀態。以不可變 record 取代可變欄位，避免併發更新的競態。 */
    private record Attempt(int failures, long lockedUntilMillis, long lastFailureMillis) {
    }
}
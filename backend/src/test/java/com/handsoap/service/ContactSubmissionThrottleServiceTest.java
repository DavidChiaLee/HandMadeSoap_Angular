package com.handsoap.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 每 IP 滑動窗口限流的行為驗證（review 728）。
 *
 * <p>以注入的 clock 推進時間，刻意<b>不</b>用 Thread.sleep ——
 * 真時鐘的測試在慢機器上會 flaky，且 sleep 會拖慢整套測試。</p>
 */
class ContactSubmissionThrottleServiceTest {

    /** 流程起點。 */
    private static final long T0 = 1_700_000_000_000L;

    private static final Duration WINDOW = Duration.ofMinutes(60);
    private static final int MAX = 5;

    private AtomicLong now;
    private ContactSubmissionThrottleService throttle;

    @BeforeEach
    void setUp() {
        now = new AtomicLong(T0);
        // 用靜態工厂而非建構子：第二個建構子會讓 Spring 容器啟動失敗（見 forTesting 註解）
        throttle = ContactSubmissionThrottleService.forTesting(MAX, WINDOW, now::get);
    }

    @Test
    @DisplayName("窗口內第 MAX 次應放行，第 MAX+1 次應被擋並回剩餘等待時間")
    void shouldBlockAfterMaxSubmissions() {
        for (int i = 0; i < MAX; i++) {
            assertThat(throttle.check("1.2.3.4")).isEqualTo(Duration.ZERO);
            throttle.recordSuccess("1.2.3.4");
        }

        Duration remaining = throttle.check("1.2.3.4");
        assertThat(remaining).isGreaterThan(Duration.ZERO);
        assertThat(remaining).isLessThanOrEqualTo(WINDOW);
    }

    @Test
    @DisplayName("check 只檢查不計數：連續多次 check 不應吃掉額度")
    void checkShouldNotConsumeQuota() {
        for (int i = 0; i < 10; i++) {
            assertThat(throttle.check("1.2.3.4")).isEqualTo(Duration.ZERO);
        }
        // 額度仍完整：還能真正送出 MAX 次
        for (int i = 0; i < MAX; i++) {
            throttle.recordSuccess("1.2.3.4");
        }
        assertThat(throttle.check("1.2.3.4")).isGreaterThan(Duration.ZERO);
    }

    @Test
    @DisplayName("不同 IP 應各自獨立計數，不可共用同一個 key")
    void shouldCountPerIp() {
        for (int i = 0; i < MAX; i++) {
            throttle.recordSuccess("1.2.3.4");
        }
        assertThat(throttle.check("1.2.3.4")).isGreaterThan(Duration.ZERO);

        // 另一個 IP 完全不受影響
        assertThat(throttle.check("5.6.7.8")).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("滑動窗口：最舊一筆滿窗口後應重新放行（非固定窗口）")
    void shouldSlideWindow() {
        throttle.recordSuccess("1.2.3.4"); // T0
        for (int i = 1; i < MAX; i++) {
            now.addAndGet(Duration.ofMinutes(10).toMillis()); // T0+10m .. T0+40m
            throttle.recordSuccess("1.2.3.4");
        }
        // 此時 5 筆分布於 T0..T0+40m，皆在 60m 窗口內 → 被擋
        assertThat(throttle.check("1.2.3.4")).isGreaterThan(Duration.ZERO);

        // 推進到 T0+60m：T0 那筆已滑出窗口，剩 4 筆 → 放行
        now.set(T0 + WINDOW.toMillis());
        assertThat(throttle.check("1.2.3.4")).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("剩餘等待時間應隨最舊一筆滑出而縮短（供 Retry-After 使用）")
    void shouldReturnDecreasingRetryAfter() {
        for (int i = 0; i < MAX; i++) {
            throttle.recordSuccess("1.2.3.4");
        }
        Duration atStart = throttle.check("1.2.3.4");

        now.addAndGet(Duration.ofMinutes(30).toMillis());
        Duration atHalfway = throttle.check("1.2.3.4");

        assertThat(atHalfway).isLessThan(atStart);
        assertThat(atHalfway).isGreaterThanOrEqualTo(Duration.ZERO);
    }
}
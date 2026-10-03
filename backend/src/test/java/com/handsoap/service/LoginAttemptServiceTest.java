package com.handsoap.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LoginAttemptService} 的限流行為驗證（review H5）。
 *
 * <p>這些測試是 H5 的真正防護網：把指數退避的門檻或邏輯改錯時必須立刻變紅。
 * 特別留意 {@link #shouldNotBlockLegitimateUserAfterFewMistypedPasswords()} ——
 * 它保護的是「不要為了防爆破而鎖死真人」這個設計前提。</p>
 */
class LoginAttemptServiceTest {

    private static final String USERNAME = "admin";
    private static final String IP = "203.0.113.10";

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        // username 門檻 5、IP 門檻 30，與 application.properties 的預設一致
        service = new LoginAttemptService(5, 30);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @DisplayName("門檻內的失敗次數不應封鎖（不打擾只是打錯密碼的真人）")
    void shouldNotBlockWithinThreshold(int failures) {
        for (int i = 0; i < failures; i++) {
            service.recordFailure(USERNAME, IP);
        }

        assertThat(service.isBlocked(USERNAME, IP))
                .as("第 %d 次失敗仍在門檻(5)內，不應封鎖", failures)
                .isFalse();
    }

    @Test
    @DisplayName("超過門檻後應封鎖，且回報剩餘等待時間")
    void shouldBlockAfterExceedingThreshold() {
        for (int i = 0; i < 6; i++) {
            service.recordFailure(USERNAME, IP);
        }

        assertThat(service.isBlocked(USERNAME, IP)).isTrue();
        assertThat(service.remainingLockDuration(USERNAME, IP))
                .as("被封鎖時應回報大於 0 的等待時間")
                .isGreaterThan(Duration.ZERO);
    }

    /**
     * 核心設計前提：打錯幾次密碼不該讓人永遠進不去。
     *
     * <p>這是刻意不用「N 次鎖 M 分鐘」的原因 —— 真人忘記密碼時必須還有機會，
     * 而攻擊者面對的是不斷倍增的等待時間。</p>
     */
    @Test
    @DisplayName("少量打錯密碼後不應累積成長期封鎖（避免鎖死真人，review H5）")
    void shouldNotBlockLegitimateUserAfterFewMistypedPasswords() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure(USERNAME, IP);
        }

        assertThat(service.isBlocked(USERNAME, IP))
                .as("3 次打錯不應被封鎖 —— 否則忘記密碼的真人要等 15 分鐘以上")
                .isFalse();
    }

    /**
     * 成功登入清空計數。
     *
     * <p>這是避免真人被鎖死的關鍵：終於想起密碼並登入後，先前累積的封鎖要消失。</p>
     */
    @Test
    @DisplayName("成功登入後應清空失敗計數")
    void shouldResetCountersOnSuccess() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure(USERNAME, IP);
        }
        service.recordSuccess(USERNAME, IP);

        for (int i = 0; i < 5; i++) {
            service.recordFailure(USERNAME, IP);
        }

        assertThat(service.isBlocked(USERNAME, IP))
                .as("成功登入後計數歸零，再失敗 5 次仍在門檻內")
                .isFalse();
    }

    @Test
    @DisplayName("username 與 IP 的計數必須互相獨立")
    void shouldKeepUsernameAndIpCountersIndependent() {
        for (int i = 0; i < 6; i++) {
            service.recordFailure(USERNAME, IP);
        }

        assertThat(service.isBlocked(USERNAME, IP)).isTrue();
        assertThat(service.isBlocked("other-user", "198.51.100.1"))
                .as("其他帳號與其他 IP 不應被這次失敗連帶封鎖")
                .isFalse();
    }

    /**
     * 攻擊者換 IP 應仍被 username 計數擋下。
     *
     * <p>反過來，換 username 也應被 IP 計數擋下 —— 這是帳號列舉防線。
     * 任一方向漏掉，攻擊者就能用最便宜的方式繞過限流。</p>
     */
    @Test
    @DisplayName("換 IP 不應繞過 username 的封鎖（防繞過）")
    void shouldNotAllowIpRotationToBypassUsernameLock() {
        for (int i = 0; i < 6; i++) {
            service.recordFailure(USERNAME, IP);
        }

        assertThat(service.isBlocked(USERNAME, "10.0.0.99"))
                .as("攻擊者換 IP 後，username 仍已被封鎖應擋下")
                .isTrue();
    }

    @Test
    @DisplayName("換 username 不應繞過同一 IP 的封鎖（防止帳號列舉）")
    void shouldNotAllowUsernameRotationToBypassIpLock() {
        LoginAttemptService ipLocked = new LoginAttemptService(5, 3);
        for (int i = 0; i < 4; i++) {
            ipLocked.recordFailure(USERNAME, IP);
        }

        assertThat(ipLocked.isBlocked("another-user", IP))
                .as("同一 IP 嘗試多個帳號應被擋下，否則可列舉有效帳號")
                .isTrue();
    }

    /**
     * 指數退避：失敗越多，等待時間越長，且以 30 分鐘封頂。
     *
     * <p>這是讓爆破「時間成本高到不具經濟性」的機制。若有人把指數退避
     * 改成固定延遲，爆破就會重新變得可行。</p>
     */
    @ParameterizedTest
    @CsvSource({"6, 30", "7, 60", "8, 120"})
    @DisplayName("等待時間應隨失敗次數指數成長（每次至少為前一級的兩倍）")
    void shouldExponentiallyBackOff(int failures, long expectedSeconds) {
        for (int i = 0; i < failures; i++) {
            service.recordFailure(USERNAME, IP);
        }

        Duration remaining = service.remainingLockDuration(USERNAME, IP);

        // 允許些許誤差（計時與 toSeconds() 的取整），用範圍而非精確相等
        assertThat(remaining.getSeconds())
                .as("第 %d 次失敗後應等待約 %d 秒", failures, expectedSeconds)
                .isBetween(expectedSeconds - 2, expectedSeconds + 2);
    }

    @Test
    @DisplayName("失敗次數再多也不應超過 30 分鐘上限")
    void shouldCapLockDurationAt30Minutes() {
        for (int i = 0; i < 100; i++) {
            service.recordFailure(USERNAME, IP);
        }

        assertThat(service.remainingLockDuration(USERNAME, IP))
                .as("再往上成長只會讓紀錄失真，應封頂在 30 分鐘")
                .isLessThanOrEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("username 門檻應比 IP 嚴格（IP 常是共用出口，不可誤傷）")
    void shouldUseStricterThresholdForUsername() {
        LoginAttemptService strictIp = new LoginAttemptService(5, 30);

        // 連續嘗試 10 個不同帳號但同一 IP：username 各自未達門檻
        for (int i = 0; i < 10; i++) {
            strictIp.recordFailure("user-" + i, IP);
        }

        assertThat(strictIp.isBlocked("user-99", IP))
                .as("IP 門檻 30，10 次不同帳號不應被封鎖（否則共用出口的真人會被誤傷）")
                .isFalse();
    }

    @Test
    @DisplayName("reset 應清除所有紀錄")
    void shouldResetAllCounters() {
        for (int i = 0; i < 10; i++) {
            service.recordFailure(USERNAME, IP);
        }
        service.reset();

        assertThat(service.isBlocked(USERNAME, IP)).isFalse();
    }
}
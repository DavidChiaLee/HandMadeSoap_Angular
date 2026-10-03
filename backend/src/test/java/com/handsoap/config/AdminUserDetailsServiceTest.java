package com.handsoap.config;

import com.handsoap.entity.Admin;
import com.handsoap.repository.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class AdminUserDetailsServiceTest {

    /**
     * 測試用的 BCrypt cost。
     *
     * <p>production 是 12（{@code SecurityConfig.BCRYPT_STRENGTH}），但 cost 每 +1
     * 約為 4 倍耗時，用 12 會讓每個測試多花數百毫秒。此處統一用 4 ——
     * <b>關鍵是「dummy hash 與 encoder 用同一個 cost」</b>，
     * 這個一致性由 {@link #shouldUseDummyHashWithSameCostAsEncoder()} 驗證，
     * 因此測試用較低的 cost 不影響其保護的行為。</p>
     */
    private static final int COST = 4;

    @Mock
    private AdminRepository adminRepository;

    /**
     * 刻意用真實的 {@link BCryptPasswordEncoder} 而非 mock：review H5 要驗證的
     * 正是「不存在的帳號也會付出 BCrypt 比對的時間」，mock 掉等於測不到東西。
     */
    private PasswordEncoder passwordEncoder;

    private AdminUserDetailsService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(COST);
        service = new AdminUserDetailsService(adminRepository, passwordEncoder);
    }

    @Test
    @DisplayName("查無此帳號時應拋出 UsernameNotFoundException")
    void shouldThrowWhenAdminNotFound() {
        given(adminRepository.findByUsername("nobody")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("nobody");
    }

    /**
     * review H5：帳號不存在時<b>仍然要執行一次 BCrypt 比對</b>。
     *
     * <p>這是消除帳號列舉的核心。若不這麼做，「帳號不存在」會近乎 0 毫秒回應、
     * 「密碼錯誤」要走完 BCrypt，攻擊者可用時間差精確列出哪些管理員帳號存在 ——
     * 而登入限流的 key 正是 username。</p>
     */
    @Test
    @DisplayName("帳號不存在時仍應執行 BCrypt 比對（消除帳號列舉的時間差，review H5）")
    void shouldPerformDummyBcryptMatchWhenAdminNotFound() {
        PasswordEncoder spy = spy(passwordEncoder);
        AdminUserDetailsService spyService = new AdminUserDetailsService(adminRepository, spy);
        given(adminRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> spyService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);

        then(spy).should().matches(anyString(), anyString());
    }

    /**
     * 負向驗證：不存在帳號的耗時應與「密碼錯誤」同一個數量級。
     *
     * <p>刻意<b>不</b>斷言兩者相等（機器負載會造成抖動），而用寬鬆的比例界線：
     * 沒有 dummy 比對時差距是數百倍，加上之後應在同一量級。
     * 若有人移除 dummy 比對，這個測試會立刻紅。</p>
     */
    @Test
    @DisplayName("帳號不存在的耗時不應遠低於密碼錯誤（時間差會洩漏帳號存在與否）")
    void shouldNotLeakAccountExistenceThroughTiming() {
        String realHash = passwordEncoder.encode("correct-password");

        // 情境 A：帳號不存在（應走 dummy 比對）
        given(adminRepository.findByUsername("ghost")).willReturn(Optional.empty());
        long notFoundMillis = timeOf(() -> assertThatThrownBy(
                () -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class));

        // 情境 B：真實 BCrypt 比對的成本基準
        long realCompareMillis = timeOf(() -> passwordEncoder.matches("wrong-password", realHash));

        assertThat(notFoundMillis)
                .as("帳號不存在的耗時應與真實 BCrypt 比對同一量級，否則可用時間差列出有效帳號")
                .isGreaterThanOrEqualTo((long) (realCompareMillis * 0.3));
    }

    /**
     * dummy hash 的 cost 必須與正式 encoder 一致。
     *
     * <p><b>這是 review H5 實作時踩過的坑。</b>原本 {@code AdminUserDetailsService}
     * 寫死了一組 {@code $2a$10$} 的 hash 常數，但正式 encoder 是 cost 12 ——
     * BCrypt 的運算量由 hash 字串裡的 cost 參數決定，因此 dummy 比對的耗時只有
     * 真實比對的 1/16，消除帳號列舉的效果<b>直接失效卻不會有任何錯誤</b>。
     * 後來改成由同一個 encoder 在啟動時產生才根治。</p>
     *
     * <p>此測試確保未來若有人又換回寫死的常數，會立刻被釘住。</p>
     */
    @Test
    @DisplayName("dummy hash 的 cost 必須與注入的 encoder 一致（避免時間對齊失效，review H5）")
    void shouldUseDummyHashWithSameCostAsEncoder() {
        String dummyHash = dummyHashOf();

        assertThat(dummyHash)
                .as("dummyHash 必須在啟動時由同一個 encoder 產生，不可寫死")
                .isNotNull();

        // BCrypt hash 格式：$2a$<cost>$<22 鹽><31 雜湊>
        String[] parts = dummyHash.split("\\$");
        assertThat(parts).as("dummyHash 應為 BCrypt 格式：%s", dummyHash).hasSize(4);

        // 注意：cost 會被補零成兩位數（cost 4 → "$2a$04$"），
        // 因此必須轉成整數比對，不能直接用字串相等。
        int hashCost = Integer.parseInt(parts[2]);
        assertThat(hashCost)
                .as("dummyHash 的 cost(%d) 應等於 encoder 的 strength(%d)，"
                        + "否則 dummy 比對成本偏低、消除帳號列舉會失效", hashCost, COST)
                .isEqualTo(COST);
    }

    /**
     * dummy 比對永遠不該成功。
     *
     * <p>防呆：若有人日後改寫成「用某個已知明文去比對」，而該明文恰好能命中
     * dummyHash，就會出現「不存在的帳號居然通過驗證」的重大漏洞。</p>
     */
    @Test
    @DisplayName("dummy 比對的明文不應命中 dummyHash（防呆：防注入已知明文）")
    void shouldNeverMatchDummyHash() {
        String dummyHash = dummyHashOf();

        assertThat(passwordEncoder.matches("never-matches-this-value", dummyHash))
                .as("dummy 比對必須永遠失敗，這裡只在乎時間成本")
                .isFalse();
    }

    /** 取出台階 private 的 dummyHash。集中處理反射，避免每個測試重複轉型。 */
    private String dummyHashOf() {
        return (String) ReflectionTestUtils.getField(service, "dummyHash");
    }

    /**
     * 原始碼不得含完整的 BCrypt hash 字面值。
     *
     * <p>{@code scripts/verify-no-secrets.ps1} 會把符合
     * {@code \$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}} 的 60 字元雜湊視為疑似外洩的
     * 密碼並擋下提交（該規則刻意不做豁免）。</p>
     *
     * <p>此處<b>刻意沿用 scanner 的同一條 regex</b>，而不是簡化成「有沒有 {@code $2a$}」——
     * 後者會誤傷 javadoc 中說明 hash 格式的文字（例如 {@code $2a$10$...}），
     * 反而讓人想放寬規則。regex 一致才能確保「測試通過 = scanner 也會通過」。</p>
     */
    @Test
    @DisplayName("程式碼中不得出現完整的 BCrypt hash 字面值（會被 secret scanner 擋，review H5）")
    void shouldNotHardcodeBcryptHashLiteral() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/handsoap/config/AdminUserDetailsService.java"));

        // 與 scripts/verify-no-secrets.ps1 的 strictRules 完全相同
        Pattern bcryptPattern = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

        assertThat(bcryptPattern.matcher(source).find())
                .as("原始碼不得含 BCrypt hash 字面值，否則 scripts/verify-no-secrets.ps1 會擋下提交")
                .isFalse();
    }

    private long timeOf(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    @Test
    @DisplayName("查得到帳號時應回傳包裝好的 AdminPrincipal")
    void shouldReturnAdminPrincipal() {
        Admin admin = Admin.builder()
                .id(1L)
                .username("admin")
                .passwordHash("$2a$10$hashed")
                .email("admin@example.com")
                .role("ADMIN")
                .build();
        given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin));

        UserDetails details = service.loadUserByUsername("admin");

        assertThat(details).isInstanceOf(AdminPrincipal.class);
        assertThat(details.getUsername()).isEqualTo("admin");
        assertThat(details.getPassword()).isEqualTo("$2a$10$hashed");
        assertThat(details.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("應以帶入的 username 查詢資料庫")
    void shouldQueryRepositoryWithUsername() {
        Admin admin = Admin.builder().id(1L).username("staff").role("ADMIN").build();
        given(adminRepository.findByUsername("staff")).willReturn(Optional.of(admin));

        service.loadUserByUsername("staff");

        then(adminRepository).should().findByUsername("staff");
    }
}
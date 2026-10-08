package com.handsoap.config;

import com.handsoap.controller.AdminController;
import com.handsoap.controller.ControllerTestSupport;
import com.handsoap.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} 搭配 SecurityConfig 驗證授權鏈。
 *
 * <p>只指定 {@link AdminController}：授權測試關心的是「請求有沒有被擋下」，
 * 而 SecurityConfig 的 URL 規則與實際是哪個 Controller 無關 —— 未登入的請求會在
 * 進入 Controller 之前就被 security filter chain 擋掉。
 * 不指定 Controller 的話（bare {@code @WebMvcTest}）會載入全部 Controller，
 * 需要為每個 service 都補 {@code @MockBean}，徒增維護成本。</p>
 *
 * <p>服務層以 {@code @MockBean} 注入；授權測試不需要真實回應內容。</p>
 */
@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, ControllerTestSupport.class})
class SecurityConfigTest {

    private final SecurityConfig config =
            new SecurityConfig(mock(JwtAuthFilter.class), "http://localhost:4200");

    @Autowired
    private MockMvc mockMvc;

    /** AdminController 唯一的依賴；授權測試只需它能被注入即可。 */
    @MockBean
    private AdminService adminService;

    @Test
    @DisplayName("CORS 應開放 /api/** 且允許常見方法與任意標頭")
    void shouldConfigureCorsForApiPaths() {
        CorsConfiguration cors = resolveApiCors();

        assertThat(cors).isNotNull();
        // review H4：由設定注入的白名單取代原本的萬用字元 "*"
        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:4200");
        assertThat(cors.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(cors.getAllowedHeaders()).containsExactly("*");
    }

    /**
     * 防止 H4 被無聲還原成萬用字元。
     *
     * <p>舊版斷言的是 {@code containsExactly("*")}，正是我們要移除的設定。
     * 現在改成反向斷言：白名單絕不可包含 {@code *}。</p>
     */
    @Test
    @DisplayName("CORS 白名單不得包含萬用字元 *（review H4）")
    void shouldNotAllowWildcardOrigin() {
        CorsConfiguration cors = resolveApiCors();

        assertThat(cors.getAllowedOrigins())
                .as("萬用字元會讓任意網站都能跨站呼叫 API，等同 H4 未修正")
                .doesNotContain("*");
    }

    /**
     * {@code allowCredentials} 為 true 是<b>刻意的</b>（M8 Phase 2 起），不是疏忽。
     *
     * <p>本系統以 JWT 認證，token 走 {@code Authorization} 標頭，前端沒有使用
     * {@code withCredentials}，因此不需要 cookie 型認證。若日後有人圖省事改成
     * {@code true}，會與白名單模式產生預期外的行為，這裡釘住現況。</p>
     */
    @Test
    @DisplayName("allowCredentials 應為 true（M8 Phase 2：refresh token 走 HttpOnly Cookie，需跨站帶 Cookie）")
    void shouldAllowCredentials() {
        CorsConfiguration cors = resolveApiCors();

        assertThat(Boolean.TRUE.equals(cors.getAllowCredentials()))
                .as("refresh Cookie 需跨站傳送，credentials 必須開啟")
                .isTrue();
    }

    /** SecurityConfig 宣告回傳 CorsConfigurationSource 介面，實際物件為 UrlBasedCorsConfigurationSource。 */
    private CorsConfiguration resolveApiCors() {
        CorsConfigurationSource source = config.corsConfigurationSource();
        assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);
        return ((UrlBasedCorsConfigurationSource) source).getCorsConfigurations().get("/api/**");
    }

    @Test
    @DisplayName("passwordEncoder 應為 BCrypt，且可正確驗證密碼")
    void shouldProvideWorkingBcryptPasswordEncoder() {
        PasswordEncoder encoder = config.passwordEncoder();

        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);

        String raw = "my-secret-pass";
        String encoded = encoder.encode(raw);
        assertThat(encoded).isNotEqualTo(raw);
        assertThat(encoder.matches(raw, encoded)).isTrue();
        assertThat(encoder.matches("wrong-pass", encoded)).isFalse();
    }

    @Test
    @DisplayName("同一密碼兩次加密應產生不同結果（含 salt）")
    void shouldProduceDifferentHashEachTime() {
        PasswordEncoder encoder = config.passwordEncoder();

        assertThat(encoder.encode("same")).isNotEqualTo(encoder.encode("same"));
    }

    /**
     * 防的是 review C4 回歸：{@code POST /api/v1/admin/admins} 在舊版是 permitAll，
     * 讓「部署後、owner 建立帳號前」 anyone 都能搶註 ADMIN。首位管理員現已改由
     * {@code AdminBootstrapRunner} 於啟動時建立。
     *
     * <p>注意這些路徑全都被 {@code /api/v1/admin/**} 明確列舉，因此本測試
     * <b>不</b>負責偵測 review H3 的 fail-open 回歸 —— 見下方
     * {@link #shouldDenyAnonymousAccessToUnlistedEndpoints(String)}。
     *
     * <p>用 MockMvc 實際打一次請求來驗證授權鏈，比直接操作 SecurityContext
     * 物件更貼近真實行為，也較不受 Spring Security 內部 API 變動影響。</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/admin/admins",
            "/api/v1/admin/soaps",
            "/api/v1/admin/stats",
            "/api/admin/admins"
    })
    @DisplayName("未登入不得存取任何管理端點（含已移除 bootstrap 的 POST /api/v1/admin/admins）")
    void shouldDenyAnonymousAccessToAdminEndpoints(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * review H3 的真正防護網：SecurityConfig 完全沒有列舉的端點，匿名不得存取。
     *
     * <p>為什麼需要這個測試：{@link #shouldDenyAnonymousAccessToAdminEndpoints(String)}
     * 打的路徑都被 {@code /api/v1/admin/**} 明確列舉，因此只要
     * {@code anyRequest()} 退回 {@code permitAll()}，那個測試依然會綠 ——
     * 對 fail-open 回歸零鑑別力。這裡改打「沒有任何 matcher 覆蓋」的路徑，
     * 才是唯一能真正抓出 {@code anyRequest()} 被改回去的測試。</p>
     *
     * <p>這些路徑在 {@code @WebMvcTest(AdminController.class)} 中沒有對應的 Handler，
     * 本該回 404。但 {@code AuthorizationFilter} 執行於 {@code DispatcherServlet}
     * <b>之前</b>，請求會先被授權擋下 → 直接 401，永遠走不到 404。
     * 這也代表本測試在 {@code anyRequest()} 退回 {@code permitAll()} 時會立刻變紅。</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v2/soaps",        // 版本前綴不同，不應被現有 matcher 誤放行
            "/api/v1/unknown",      // 未定義的 API
            "/internal/secret",     // 非 /api 前綴
            // 注意：/actuator/health 曾在此列（預設不公開），但 review M7 引入
            // actuator 後已顯式 permitAll 供 compose 探針使用，故移至下方
            // shouldAllowAnonymousAccessToHealthEndpoint，見該處說明。
            "/",                    // 根路徑
            "/index.html"
    })
    @DisplayName("未在 SecurityConfig 列舉的端點不得匿名存取（fail-closed，review H3）")
    void shouldDenyAnonymousAccessToUnlistedEndpoints(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 健康探針必須匿名可達（review M7）。
     *
     * <p>compose healthcheck 打 {@code /actuator/health/readiness} 擋 nginx 進流量，
     * 探針不帶 JWT —— 若此放行被移除，backend 永遠 unhealthy，nginx 也永遠不啟。
     * 只放行 health（含子路徑）：打 {@code /actuator/env} 等仍須 401，
     * 防止日後有人圖省事放行整個 {@code /actuator/**}。</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator/health",
            "/actuator/health/readiness",
            "/actuator/health/liveness"
    })
    @DisplayName("健康探針應匿名可達（僅 health；其餘 actuator 端點仍須擋下）")
    void shouldAllowAnonymousAccessToHealthEndpoint(String path) throws Exception {
        int status = mockMvc.perform(get(path))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status)
                .as("探針 %s 不應被授權擋下", path)
                .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
    }

    /**
     * CORS 預檢（preflight）不得被 {@code anyRequest().authenticated()} 擋下。
     *
     * <p>本專案是前後端分離、跨來源呼叫（dev 為 {@code localhost:4200 → :8080}），
     * 瀏覽器對 JSON／帶標頭的請求會先送 {@code OPTIONS} 預檢。Spring Security 的
     * {@code CorsFilter} 位於 {@code AuthorizationFilter} 之前，會對預檢請求短路回應，
     * 理論上不會變 401。</p>
     *
     * <p>這條測試是為了把上述「理論」變成「實測事實」：一旦哪天迴歸成 401，
     * 症狀會是前台整個站抓不到資料（但主控台只看得到 CORS 錯誤），
     * 屬於極難定位的故障。</p>
     */
    @Test
    @DisplayName("跨來源預檢（OPTIONS）不應被 anyRequest().authenticated() 擋下")
    void shouldAllowCorsPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/soaps")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().is2xxSuccessful());
    }

    /**
     * {@code /error} 必須維持匿名可達，與 {@link #shouldDenyAnonymousAccessToUnlistedEndpoints(String)}
     * 是同一件事的兩面。
     *
     * <p>Spring Security 預設攔截 ERROR dispatcher type。若這條日後被移除，
     * 未登入使用者的任何 4xx／5xx 都會被改寫成 401：真實錯誤碼被掩蓋不說，
     * 前端還會誤導使用者「請重新登入」，讓排查變得更困難。</p>
     */
    @Test
    @DisplayName("匿名請求 /error 不應被擋（否則真實 4xx/5xx 會被改寫成 401）")
    void shouldNotRequireAuthenticationForErrorPath() throws Exception {
        int status = mockMvc.perform(get("/error"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status)
                .as("/error 必須 permitAll，否則未登入使用者的錯誤碼會被改寫成 401")
                .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
    }

    /**
     * review H4 的守門測試：白名單解析規則與 fail-fast 行為。
     *
     * <p>這些測試直接呼叫 {@link SecurityConfig#parseAllowedOrigins(String)}，
     * 因為 fail-fast 發生在建構子（= Spring 建立 bean = 應用啟動）時，
     * 用 MockMvc 無法觀察 —— 應用根本不會啟動成功。</p>
     */
    @Test
    @DisplayName("多個來源應以逗號切分並去除前後空白")
    void shouldSplitAndTrimMultipleOrigins() {
        List<String> origins = SecurityConfig.parseAllowedOrigins(
                " https://a.com , http://localhost:4200 ");

        assertThat(origins).containsExactly("https://a.com", "http://localhost:4200");
    }

    @Test
    @DisplayName("重複的來源應被去重")
    void shouldDeduplicateOrigins() {
        List<String> origins = SecurityConfig.parseAllowedOrigins(
                "https://a.com, https://a.com");

        assertThat(origins).containsExactly("https://a.com");
    }

    @Test
    @DisplayName("空字串與多餘逗號應被忽略，不產生無效來源")
    void shouldIgnoreEmptySegments() {
        List<String> origins = SecurityConfig.parseAllowedOrigins("https://a.com,,  ,");

        assertThat(origins).containsExactly("https://a.com");
    }

    /**
     * 負向測試：萬用字元必須讓啟動失敗。
     *
     * <p>若放行 {@code *}，整道 H4 就被一個字元繞過了，卻沒有任何警訊 ——
     * 這與專案對 {@code JWT_SECRET} / {@code DATABASE_PASSWORD} 採取 fail-fast
     * 的既有哲學一致。</p>
     */
    @Test
    @DisplayName("含萬用字元 * 的設定應讓啟動失敗（fail-fast，review H4）")
    void shouldRejectWildcardOrigin() {
        assertThatThrownBy(() -> SecurityConfig.parseAllowedOrigins("*"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("*");

        // 即使混在多筆之中也必須被擋下
        assertThatThrownBy(() -> SecurityConfig.parseAllowedOrigins(
                "https://a.com, *"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("*");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", ",", " , , "})
    @DisplayName("空白或無實質內容的設定應讓啟動失敗（fail-fast）")
    void shouldRejectBlankOrigins(String raw) {
        assertThatThrownBy(() -> SecurityConfig.parseAllowedOrigins(raw))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
 * BCrypt cost 必須是 12（review H5 由 10 調高）。
 *
 * <p>此斷言把「安全強度」這個屬性釘住。若有人為了「登入快一點」而調低 strength，
 * 會直接看到這個測試失敗，而不是默默降低暴力猜解的成本。</p>
 */
@Test
@DisplayName("BCrypt cost 應為 12（review H5：提高暴力猜解成本）")
void shouldUseBcryptCost12() {
PasswordEncoder encoder = config.passwordEncoder();

assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);

BCryptPasswordEncoder bcrypt = (BCryptPasswordEncoder) encoder;
String encoded = bcrypt.encode("probe-password");

// BCrypt hash 內嵌 cost：$2a$12$...；cost 會補零成兩位數，故以整數比對
String[] parts = encoded.split("\\$");
assertThat(parts).as("應為 BCrypt 格式：%s", encoded).hasSize(4);
assertThat(Integer.parseInt(parts[2]))
.as("BCrypt cost 應為 %d，調低會直接降低爆破成本", SecurityConfig.BCRYPT_STRENGTH)
.isEqualTo(SecurityConfig.BCRYPT_STRENGTH);
}

/**
 * cost 12 的實際耗時實測 —— 證明「消除帳號列舉」的 dummy 比對在正式環境是有效的。
 *
 * <p>為什麼需要這個測試：H5 實作時曾把 dummy hash 寫死成 {@code $2a$10$}，
 * 而正式 encoder 是 cost 12 —— dummy 比對只有真實比對約 6% 的成本，
 * 消除帳號列舉<b>完全失效卻沒有任何錯誤</b>。既有的時間對齊測試用 cost 4，
 * 兩邊同樣偏低而互相掩蓋，沒抓到。
 *
 * <p>本測試直接以正式的 cost 12 量測 dummy 比對與真實比對的耗時比，
 * 把「兩者必須同一個數量級」變成可驗證的事實。</p>
 */
@Test
@DisplayName("以正式 cost 12 實測：dummy 比對與真實比對的耗時應在同一量級（review H5）")
void shouldAlignDummyMatchCostAtProductionStrength() {
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(SecurityConfig.BCRYPT_STRENGTH);

// 模擬 AdminUserDetailsService 的做法：由同一個 encoder 產生 dummy hash
String dummyHash = encoder.encode(UUID.randomUUID().toString());
String realHash = encoder.encode("correct-password");

// 先各跑一次暖機，避免 JIT 編譯時間干擾量測
encoder.matches("warmup-a", realHash);
encoder.matches("warmup-b", dummyHash);

long realMillis = timeOf(() -> encoder.matches("wrong-password", realHash));
long dummyMillis = timeOf(() -> encoder.matches("never-matches-this-value", dummyHash));

// 比例界線取 0.5：兩個都是同一個 cost 的 BCrypt，耗時理應非常接近；
// 若 dummy hash 的 cost 較低（原本的 bug 是 10 vs 12，約 6%），必然遠低於此界線。
assertThat(dummyMillis)
.as("dummy 比對(%dms) 應與真實比對(%dms) 同一量級 —— "
+ "偏低代表 dummy hash 的 cost 與 encoder 不一致，消除帳號列舉會失效",
dummyMillis, realMillis)
.isGreaterThanOrEqualTo((long) (realMillis * 0.5));
}

/** 以毫秒量測一段動作的耗時。刻意不用 assertTimeout，避免受 JUnit 量測機制影響。 */
    private long timeOf(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    /**
     * 反向確認：公開端點在未登入時必須仍可通過授權。
     *
     * <p>若日後有人為了「保險」把公開路徑也加上權限，前台網站會整個壞掉 ——
     * 這個測試就是防止過度收緊的護欄。</p>
     *
     * <p>此測試類只載入 AdminController，因此公開路徑會回 404（沒有對應 Controller）。
     * 重點在於「不是 401／403」—— 代表請求已通過 SecurityConfig 的授權規則，
     * 只不過沒有 Handler 可以處理。各公開端點的實際回應由其各自的 Controller 測試驗證。</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/soaps",
            "/api/v1/oils",
            "/api/v1/essential-oils",
            "/api/v1/categories"
    })
    @DisplayName("未登入仍可通過公開端點的授權檢查（防止過度收緊權限）")
    void shouldAllowAnonymousAccessToPublicEndpoints(String path) throws Exception {
        int status = mockMvc.perform(get(path))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status)
                .as("公開端點 %s 不應被權限擋下", path)
                .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
    }
}
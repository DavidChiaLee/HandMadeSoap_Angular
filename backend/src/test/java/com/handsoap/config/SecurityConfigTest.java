package com.handsoap.config;

import com.handsoap.controller.AdminController;
import com.handsoap.controller.ControllerTestSupport;
import com.handsoap.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private final SecurityConfig config = new SecurityConfig(mock(JwtAuthFilter.class));

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
        assertThat(cors.getAllowedOrigins()).containsExactly("*");
        assertThat(cors.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(cors.getAllowedHeaders()).containsExactly("*");
    }

    @Test
    @DisplayName("使用萬用 origin 時不應同時允許 credentials")
    void shouldNotAllowCredentialsWithWildcardOrigin() {
        CorsConfiguration cors = resolveApiCors();

        assertThat(Boolean.TRUE.equals(cors.getAllowCredentials())).isFalse();
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
     * review Roadmap 第 5 點要求的參數化測試：未在 SecurityConfig 明列的
     * 管理端點不得被匿名存取。
     *
     * <p>重點是 {@code POST /api/v1/admin/admins} —— 舊版它是 permitAll，
     * 導致部署後、owner 建立帳號前的時間窗可被公網搶註 ADMIN（review C4）。
     * 首位管理員現已改由 {@code AdminBootstrapRunner} 於啟動時建立。</p>
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
package com.handsoap.config;

import com.handsoap.config.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * BCrypt cost（review H5）。由 10 調高至 12：cost 每 +1 運算時間約為前一級的 4 倍。
     * 維持常數而非設定，是為了讓 {@code AdminUserDetailsService} 的 dummy hash
     * 與實際 encoder 永遠保持一致 —— 若兩者 cost 不同，消除帳號列舉的時間對齊就失效。
     */
    static final int BCRYPT_STRENGTH = 12;

    private final JwtAuthFilter jwtAuthFilter;

    /** review H4：由設定注入的 CORS 白名單，取代原本的萬用字元 "*"。 */
    private final List<String> allowedOrigins;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.allowedOrigins = parseAllowedOrigins(allowedOrigins);
        log.info("[cors] 允許的來源為 {}（逗號分隔多筆；不接受 * 萬用字元）", this.allowedOrigins);
    }

    /**
     * 解析並檢查 {@code app.cors.allowed-origins}。
     *
     * <p>依「專案既有慣例」採建構子注入（與 {@code AdminBootstrapRunner} 一致），
     * 而非新增 {@code @ConfigurationProperties} 類別：專案目前完全未使用
     * {@code @ConfigurationProperties}，且 {@code @WebMvcTest} 切片不會掃描一般
     * {@code @Configuration}（見 {@code ControllerTestSupport} 的說明），
     * 新增設定類別反而會讓測試缺 bean。</p>
     *
     * <p>fail-fast：出現 {@code *} 直接讓啟動失敗。理由與
     * {@code jwt.secret} / {@code spring.datasource.password} 刻意不給預設值相同 ——
     * 若允許 {@code *}，整道 H4 就被一個字元繞過了，卻沒有任何警訊。</p>
     */
    static List<String> parseAllowedOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins 不可為空。"
                            + "請設定為實際使用的來源，例如 CORS_ALLOWED_ORIGINS=https://your-domain.com");
        }

        List<String> origins = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();

        if (origins.isEmpty()) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins 解析後沒有任何有效來源（內容為「" + raw + "」）。");
        }
        if (origins.contains("*")) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins 不可使用萬用字元 *（目前設定：" + raw + "）。"
                            + "這會讓任意網站都能跨站呼叫本系統 API，等同 review H4 未修正。"
                            + "請改填精確來源，例如 CORS_ALLOWED_ORIGINS=https://your-domain.com");
        }

        return origins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/categories/**").permitAll()
                .requestMatchers("/api/v1/soaps/**").permitAll()
                .requestMatchers("/api/v1/oils/**").permitAll()
                .requestMatchers("/api/v1/essential-oils/**").permitAll()
                .requestMatchers("/api/v1/contacts/**").permitAll()
                // 上傳圖片由前端以 <img src="/uploads/..."> 直接載入，不帶 JWT，
                // 因此這條必須顯式放行：漏掉會讓正式站「所有圖片變 401」，
                // 而且前端只會顯示 placeholder、不會報錯，極難察覺。
                //
                // /error 放行的理由與 /uploads 不同：Spring Security 預設攔截
                // REQUEST / ASYNC / ERROR 三種 dispatcher type。若不放行 /error，
                // 未登入使用者的任何 4xx/5xx 都會被改寫成 401 —— 真實錯誤碼被掩蓋，
                // 且前端會誤導使用者「請重新登入」，反而更難排查。
                .requestMatchers("/uploads/**", "/error").permitAll()
                // 首位管理員不再透過 HTTP 建立：原本這裡對 POST /api/v1/admin/admins
                // permitAll，讓「部署後、owner 建立帳號前」 anyone 都能搶註 ADMIN（review C4）。
                // 已改由 AdminBootstrapRunner 於啟動時建立，且由 /api/v1/admin/**
                // 的 hasRole("ADMIN") 一律把關。
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // review H3：改為 authenticated() 是 fail-closed。
                // 原本的 permitAll() 讓未列舉的端點預設公開 —— 日後新增任何 controller
                // 忘了補上面的 matcher，就會靜默地對全網開放，且沒有任何編譯／測試警訊。
                //
                // 前提提醒：authenticated() 只擋「匿名」，不擋「已登入但非管理員」。
                // 目前系統只有 Admin 一種角色（Admin.getRole()），所以它與 hasRole("ADMIN")
                // 近乎等價，安全上沒問題。但若日後加入前台會員登入系統，這裡必須一併
                // 收緊成 hasRole("ADMIN") 或 denyAll()，否則一般會員就能存取任何
                // 忘記加 matcher 的新端點 —— fail-open 會以另一種形式回來。
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                // 未登入 / token 過期或無效：回 401，讓前端可偵測並引導重新登入
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"success\":false,\"message\":\"Unauthorized. Please login again.\"}");
                })
                // 已登入但權限不足：回 403
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"success\":false,\"message\":\"Forbidden. " + accessDeniedException.getMessage() + "\"}");
                })
            );

        return http.build();
    }

    /**
     * CORS 設定（review H4）。
     *
     * <p>原本是 {@code setAllowedOrigins(List.of("*"))}：任何網站都能對公開端點
     * 發起跨站讀取（資料外洩）與寫入（例如把 {@code /api/v1/contacts} 當垃圾表單轟）。
     * 現改為由 {@code app.cors.allowed-origins} 注入的白名單。</p>
     *
     * <p><b>allowCredentials 維持 false，不是疏忽。</b>本系統以 JWT 做認證，
     * token 放在 {@code Authorization} 標頭，前端沒有使用 {@code withCredentials}
     * （見 {@code frontend/src/app/app.config.ts}），因此不需要 cookie 型認證。
     * 順帶釐清一個常見誤解：{@code *} 搭配 {@code allowCredentials(false)} 在
     * CORS 規格上是合法的，Spring 只會在 {@code true} + {@code *} 時拋例外 ——
     * 舊設定真正的問題純粹是「過度寬鬆」，不是「設定無效」。</p>
     *
     * <p><b>CORS 不是存取控制，這點很重要。</b>它擋的是「第三方網頁借用使用者
     * 瀏覽器發出的請求」，攻擊者用 curl / Postman 直接打 API 完全不受影響。
     * 直接呼叫的防線是 review H3（{@code anyRequest().authenticated()}，
     * 已完成）與 H5（登入限流，尚未修）。</p>
     *
     * <p>不註冊 PATCH／HEAD：目前沒有 PATCH 端點，日後新增時記得回來補。</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 只註冊 /api/**：/uploads/** 目前由前端 <img> 直接載入，本來就不走 CORS。
        // 若日後改用 fetch() 讀圖檔，才需要把 /uploads/** 一併註冊。
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /**
     * BCrypt cost 由 10 調高至 12（review H5）。
     *
     * <p><b>存量密碼不需要重設。</b>BCrypt 的 hash 字串本身就內嵌了產生它時的 cost
     * （{@code $2a$12$...}），{@code matches()} 讀的是 hash 裡的 cost，不是這個
     * encoder 目前的 strength。因此 cost=10 時期建立的密碼仍可正常登入；
     * 只有<b>新建立／修改</b>的密碼會用 cost=12。
     *
     * <p><b>代價要清楚知道</b>：cost 每 +1，運算時間約為前一級的 4 倍。這讓登入端點
     * 本身就變成較昂貴的操作 —— 這正是為什麼登入限流（{@code LoginAttemptService}）
     * 與 nginx {@code limit_req} 必須一起做，不能只靠提高 cost 來擋爆破。</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}

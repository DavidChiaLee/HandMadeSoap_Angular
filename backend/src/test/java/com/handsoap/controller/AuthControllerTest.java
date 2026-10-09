package com.handsoap.controller;

import com.handsoap.config.ClientIpResolver;
import com.handsoap.config.SecurityConfig;
import com.handsoap.dto.LoginRequest;
import com.handsoap.dto.LoginResponse;
import com.handsoap.entity.Admin;
import com.handsoap.service.AuthService;
import com.handsoap.service.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, ControllerTestSupport.class, ClientIpResolver.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    /**
     * 限流服務（review H5）。
     *
     * <p>用 mock 而非真實實例：本測試類關注的是 Controller 的「擋下 / 放行 / 記錄」
     * 協調行為，限流本身的退避邏輯由 {@code LoginAttemptServiceTest} 獨立驗證。
     * 若這裡改用真實實例，兩邊的失敗會混在一起難以定位。</p>
     */
    @MockBean
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void resetThrottle() {
        // 每個測試預設都是「未被封鎖」。刻意<b>不</b>用 Mockito.reset()：
        // reset 會連帶清掉下面用 anyString() 建立的預設 stubbing，順序容易出錯。
        // 這裡只補上「預設放行」，各測試需要時再用自己的條件覆蓋。
        lenient().when(loginAttemptService.isBlocked(anyString(), anyString())).thenReturn(false);
        lenient().when(loginAttemptService.remainingLockDuration(anyString(), anyString()))
                .thenReturn(Duration.ZERO);
    }

    @Test
    @DisplayName("登入成功時應回 200 與 access token")
    void shouldLoginSuccessfully() throws Exception {
        given(authService.login(any(LoginRequest.class))).willReturn(LoginResponse.builder()
                .accessToken("signed-jwt")
                .username("admin")
                .role("ADMIN")
                .build());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("signed-jwt"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("登入失敗時應回 401")
    void shouldReturn401OnBadCredentials() throws Exception {
        given(authService.login(any(LoginRequest.class)))
                .willThrow(new BadCredentialsException("bad password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("登入端點不需登入即可呼叫（permitAll）")
    void shouldAllowAnonymousAccess() throws Exception {
        given(authService.login(any(LoginRequest.class)))
                .willReturn(LoginResponse.builder().accessToken("t").build());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"b\"}"))
                .andExpect(status().isOk());
    }

    // ---- review H5：限流行為 ----

    /**
     * 被封鎖時應回 <b>429</b> 而不是 401。
     *
     * <p>兩者對使用者的意義完全不同：401 = 密碼錯（該檢查輸入）、
     * 429 = 嘗試過多（該等待）。若誤回 401，使用者會不斷重試讓封鎖持續延長。</p>
     */
    @Test
    @DisplayName("已被封鎖時應回 429 而非 401，並附 Retry-After（review H5）")
    void shouldReturn429WhenBlocked() throws Exception {
        lenient().when(loginAttemptService.isBlocked(anyString(), anyString())).thenReturn(true);
        lenient().when(loginAttemptService.remainingLockDuration(anyString(), anyString()))
                .thenReturn(Duration.ofSeconds(120));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"whatever\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "120"))
                .andExpect(jsonPath("$.success").value(false));

        // 關鍵：被封鎖時<b>不應</b>呼叫 authService —— 否則限流等於形同虛設，
        // 每次還是會跑完整 BCrypt。
        verify(authService, times(0)).login(any(LoginRequest.class));
    }

    // ---- review M8 Phase 1：logout 撤銷 ----

    @Test
    @DisplayName("攜有效身分登出時應回 200 並呼叫撤銷（token_version bump）")
    void shouldLogoutWithValidPrincipal() throws Exception {
        Admin admin = Admin.builder().id(1L).username("admin").role("ADMIN").build();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(post("/api/v1/auth/logout").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).logout("admin");
    }

    /**
     * 匿名呼叫 logout 必須 401。
     *
     * <p>本端點在 /api/v1/auth/** 的 permitAll 區段內，身分只能來自
     * JwtAuthFilter 解出的 principal —— 沒有 Bearer 就沒有 Admin principal，
     * Controller 明確回 401。若這裡被放行，任何人都能用 username 參數
     * bump 別人的 token_version（把全後台登出 = 拒絕服務）。</p>
     */
    @Test
    @DisplayName("匿名呼叫 logout 應回 401 且不得 bump 任何帳號")
    void shouldReturn401WhenLogoutWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).logout(anyString());
    }

    @Test
    @DisplayName("登入失敗時應記錄失敗次數（供後續封鎖判斷）")
    void shouldRecordFailureOnBadCredentials() throws Exception {
        given(authService.login(any(LoginRequest.class)))
                .willThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        // MockMvc 未設定時 remoteAddr 為 127.0.0.1
        verify(loginAttemptService).recordFailure("admin", "127.0.0.1");
    }

    /**
     * 成功登入必須清空計數。
     *
     * <p>這是避免真人被鎖死的關鍵 —— 沒有它，打錯幾次後即使成功登入，
     * 先前累積的封鎖仍會在接下來 15 分鐘內擋住該使用者。</p>
     */
    @Test
    @DisplayName("登入成功時應清空失敗計數（避免真人被累積封鎖，review H5）")
    void shouldRecordSuccessOnLogin() throws Exception {
        given(authService.login(any(LoginRequest.class)))
                .willReturn(LoginResponse.builder().accessToken("t").build());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"right\"}"))
                .andExpect(status().isOk());

        verify(loginAttemptService).recordSuccess("admin", "127.0.0.1");
    }

    /**
     * 限流必須以真實客戶端 IP 為 key。
     *
     * <p>若誤用 {@code getRemoteAddr()}，經 nginx 反代時拿到的是 nginx 內部 IP，
     * <b>全站所有使用者會共用同一個計數器</b> —— 等於全站只能失敗 5 次就全被鎖死。
     * 這裡明確打上 X-Forwarded-For 來釘住行為。</p>
     */
    @Test
    @DisplayName("限流應以 X-Forwarded-For 的客戶端 IP 計數，不可全站共用一個 key（review H5）")
    void shouldUseForwardedClientIpForThrottling() throws Exception {
        given(authService.login(any(LoginRequest.class)))
                .willThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.77, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        // 應取第一段（最靠近客戶端），而非 nginx 自己的 IP 或整串標頭
        verify(loginAttemptService).recordFailure("admin", "203.0.113.77");
    }
}
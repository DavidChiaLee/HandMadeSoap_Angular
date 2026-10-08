package com.handsoap.controller;

import com.handsoap.dto.ApiResponse;
import com.handsoap.dto.LoginRequest;
import com.handsoap.dto.LoginResponse;
import com.handsoap.entity.Admin;
import com.handsoap.exception.TooManyLoginAttemptsException;
import com.handsoap.service.AuthService;
import com.handsoap.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * 登入端點。
 *
 * <p>限流（review H5）刻意放在 Controller 而非 Service：需要從
 * {@link HttpServletRequest} 取得用戶端 IP，而 Service 層不該依賴 servlet API。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptService loginAttemptService;

    public AuthController(AuthService authService, LoginAttemptService loginAttemptService) {
        this.authService = authService;
        this.loginAttemptService = loginAttemptService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        String username = request.getUsername();
        String clientIp = resolveClientIp(httpRequest);

        // 先擋掉已被封鎖的嘗試：這一步刻意不碰 BCrypt，因此回應很快，
        // 與下面認證失敗的慢路徑（要跑完整 BCrypt）行為明顯不同。
        // 攻擊者能從「快」知道自己是 IP 被擋、能從「慢」知道帳號存在或不存在 ——
        // 這是刻意的取捨：不那樣做就無法即時阻擋，成本是洩漏「是否被 IP 封鎖」，
        // 而帳號存在與否則仍由 AdminUserDetailsService 的 dummy 比對掩蓋。
        if (loginAttemptService.isBlocked(username, clientIp)) {
            long retryAfter = loginAttemptService.remainingLockDuration(username, clientIp).toSeconds();
            throw new TooManyLoginAttemptsException(
                    "嘗試次數過多，請於 " + Math.max(retryAfter, 1) + " 秒後再試", retryAfter);
        }

        try {
            LoginResponse loginResponse = authService.login(request);
            // 成功即清空計數，避免真人忘記密碼時累積的失敗紀錄延續成封鎖。
            loginAttemptService.recordSuccess(username, clientIp);

            // ── 把 refresh token 寫成 HttpOnly + SameSite=Strict Cookie ──
            // SameSite=Strict：跨站請求一律不帶此 Cookie（防 CSRF 竊用）。
            // Secure：僅在 HTTPS 下由瀏覽器儲存／送出；本機 http 開發時必須為 false，
            // 否則瀏覽器直接丟棄 Cookie，refresh 流程在本機永遠測不出來。
            // 判斷依據用 httpRequest.isSecure()（server.forward-headers-strategy=framework
            // 會讓 nginx 後的正式站正確回報 https），不寫死 true/false。
            String refreshToken = loginResponse.getRefreshToken();
            if (refreshToken != null && !refreshToken.isBlank()) {
                ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                        .httpOnly(true)
                        .secure(httpRequest.isSecure())
                        .sameSite("Strict")
                        .path("/")
                        .maxAge(Duration.ofDays(7))
                        .build();
                response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
                // refresh token 只走 Cookie：JSON body 一律抹掉，避免任何 JS／log／
                // devtools response 頁籤讀到明文（HttpOnly 的意義就在這裡）。
                loginResponse.setRefreshToken(null);
            }

            return ResponseEntity.ok(ApiResponse.success("Login successful", loginResponse));
        } catch (RuntimeException ex) {
            loginAttemptService.recordFailure(username, clientIp);
            throw ex;
        }
    }

    /**
     * 刷新 access token（替換過期或即將過期的 bearer token）。
     *
     * <p>只讀取 HttpOnly Cookie 裡的 {@code refresh_token}，不要求任何
     * Authorization header。步驟：
     * <ol>
     *   <li>從 Cookie 取出 {@code refresh_token}</li>
     *   <li>查 DB 找出對應 {@code admin}（透過 SHA-256 雜湊值）</li>
     *   <li>檢查 refresh token 未過期</li>
     *   <li>簽發新 access token（回覆 JSON，供前端存進記憶體；不換 refresh token）</li>
     * </ol>
     *
     * <p>本端點為 {@code permitAll}：發起 refresh 時候生時的使用者往往已無有效
     * access token，所以不能先靠 JwtAuthFilter 要求身分。任何失敗（找不到
     * token、過期、比對錯誤）都回 401，不洩漏內部細節。
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized. Please login again."));
        }

        try {
            LoginResponse loginResponse = authService.refresh(refreshToken);
            // 同 login：refresh token 不回 JSON body，只留在瀏覽器既有 Cookie。
            loginResponse.setRefreshToken(null);
            return ResponseEntity.ok(ApiResponse.success("Refresh successful", loginResponse));
        } catch (Exception e) {
            // 任何失敗（找不到 token、過期、比對錯誤）都回 401，不洩漏內部細節
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized. Please login again."));
        }
    }

    /**
     * 登出即撤銷（review M8 Phase 1）。
     *
     * <p>本端點在 {@code /api/v1/auth/**} 的 permitAll 區段內，但身分完全靠
     * {@code JwtAuthFilter} 從 Authorization header 解出的 principal ——
     * 所以「匿名呼叫」拿不到 Admin principal，這裡明確回 401，
     * 不可能用 username 參數 bump 別人的 token_version。</p>
     *
     * <p>principal 是 {@code Admin} 物件（filter 設的），不是字串 ——
     * 用 {@code instanceof} 兜住任何非預期 principal 型別。</p>
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal Object principal,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (!(principal instanceof Admin admin)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized. Please login again."));
        }

        authService.logout(admin.getUsername());
        // 登出同時清掉瀏覽器的 refresh Cookie：即使 DB 欄位已清空，不留殘留 Cookie
        // 讓瀏覽器下次誤帶。path=/ 必須與 login 時一致，否則清不掉。
        ResponseCookie clearCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
        return ResponseEntity.ok(ApiResponse.success("Logged out", null));
    }

    /**
     * 取出用戶端 IP。
     *
     * <p>優先取 {@code X-Forwarded-For} 的<b>第一段</b>（最靠近客戶端的那個），
     * 因為經過 nginx 反代時 {@code getRemoteAddr()} 拿到的是 nginx 的內部 IP，
     * 若用它當限流 key，<b>所有使用者會共用同一個計數器</b> —— 等於全站只能失敗幾次。
     *
     * <p>安全提醒：{@code X-Forwarded-For} 可被客戶端偽造，這裡信任它的前提是
     * 後端不直接對外（docker-compose 已刻意不發布 ports）。若日後後端開放對外，
     * 必須改為信任來自已知名單 proxy 的標頭，或直接採用 {@code getRemoteAddr()}。</p>
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            String first = comma > 0 ? forwardedFor.substring(0, comma) : forwardedFor;
            if (!first.isBlank()) {
                return first.trim();
            }
        }
        return request.getRemoteAddr();
    }
}

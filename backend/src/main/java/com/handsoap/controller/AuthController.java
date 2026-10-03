package com.handsoap.controller;

import com.handsoap.dto.ApiResponse;
import com.handsoap.dto.LoginRequest;
import com.handsoap.dto.LoginResponse;
import com.handsoap.exception.TooManyLoginAttemptsException;
import com.handsoap.service.AuthService;
import com.handsoap.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            HttpServletRequest httpRequest) {

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
            LoginResponse response = authService.login(request);
            // 成功即清空計數，避免真人忘記密碼時累積的失敗紀錄延續成封鎖。
            loginAttemptService.recordSuccess(username, clientIp);
            return ResponseEntity.ok(ApiResponse.success("Login successful", response));
        } catch (RuntimeException ex) {
            loginAttemptService.recordFailure(username, clientIp);
            throw ex;
        }
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

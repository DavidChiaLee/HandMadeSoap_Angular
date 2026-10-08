package com.handsoap.service;

import com.handsoap.config.JwtUtil;
import com.handsoap.dto.LoginRequest;
import com.handsoap.dto.LoginResponse;
import com.handsoap.entity.Admin;
import com.handsoap.repository.AdminRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(AuthenticationManager authenticationManager,
                       AdminRepository adminRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        Admin admin = adminRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        admin.setLastLoginAt(java.time.LocalDateTime.now());

        // 1) access token（短命，15 分鐘；JwtUtil 會讀 jwt.expiration-ms）
        String accessToken = jwtUtil.generateToken(
                admin.getUsername(), admin.getRole(), admin.getTokenVersion());

        // 2) refresh token：簽發 + Hash 寫入 DB；交給 HttpOnly Cookie 給前端
        String refreshToken = jwtUtil.generateRefreshToken();
        String refreshTokenHash = jwtUtil.hashRefreshToken(refreshToken);
        admin.setRefreshTokenHash(refreshTokenHash);
        admin.setRefreshTokenExpiresAt(java.time.LocalDateTime.now().plusDays(7));

        adminRepository.save(admin);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .username(admin.getUsername())
                .role(admin.getRole())
                .build();
    }

    /**
     * 刷新 access token。
     *
     * <p>從 HttpOnly Cookie 讀取 {@code refresh_token}，查 DB 找出對應 admin
     *（透過 SHA-256 雜湊後的 {@code refresh_token_hash}），檢查未過期，
     * 再簽發新 access token。刷新成功時維持原 refresh token（不換），後續再決定是否
     * 轉 rotation。
     *
     * @param refreshToken 來自 HttpOnly Cookie 的 refresh token
     * @return 新 access token 的 LoginResponse
     */
    @Transactional
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("Invalid refresh token");
        }

        // 1) 用 refresh token 雜湊值找出 admin
        String refreshTokenHash = jwtUtil.hashRefreshToken(refreshToken);
        Admin admin = adminRepository.findByRefreshTokenHash(refreshTokenHash)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        // 2) 檢查 refresh token 是否過期
        LocalDateTime expiresAt = admin.getRefreshTokenExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(java.time.LocalDateTime.now())) {
            // 過期則清掉該 admin 的 refresh 欄位（絕不能 delete(admin) —— 那會刪帳號）。
            // 清掉後下次 refresh 查不到 hash，直接 401，語意等同「refresh 已失效」。
            admin.setRefreshTokenHash(null);
            admin.setRefreshTokenExpiresAt(null);
            adminRepository.save(admin);
            throw new RuntimeException("Refresh token expired");
        }

        // 3) 簽發新 access token（當前 token_version；若登出已 +1 此 token 會因 tv
        //    claim 不符而在後續 API 層被拒，故手續仍是全球撤銷的收斂）
        String accessToken = jwtUtil.generateToken(
                admin.getUsername(), admin.getRole(), admin.getTokenVersion());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .username(admin.getUsername())
                .role(admin.getRole())
                .refreshToken(refreshToken) // 保持原 refresh token（簡化：未做 rotation）
                .build();
    }

    /**
     * 登出即撤銷（review M8 Phase 1）。
     *
     * <p>{@code token_version +1} 讓該 admin 手上<b>所有</b>未過期的 token
     * （含其他裝置）在下一個請求就因 tv claim 不符而失效 —— 弥補舊版
     * 「登出只清 localStorage、後端 token 依然活到 24h 過期」的缺口。</p>
     *
     * <p>找不到帳號時拋例外（controller 傳進來的 principal 必然是
     * 已通過 filter 驗證的 Admin，此分支僅防併發刪帳號的極端情況）。</p>
     *
     * <p>審計：沿用 H5 的 {@code [audit]} 前綴，撤銷事件必須可 grep
     * （離職/帳號被盜時的事後取證）。</p>
     */
    @Transactional
    public void logout(String username) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        admin.setTokenVersion(admin.getTokenVersion() + 1);
        // 同步作廢 refresh token：否則舊 refresh 仍有效，/auth/refresh 會用新 tv
        // 簽出新 access token，等於撤銷被繞過（登出後仍能一直續杯）。
        admin.setRefreshTokenHash(null);
        admin.setRefreshTokenExpiresAt(null);
        adminRepository.save(admin);

        log.info("[audit] logout-revoked username={} tokenVersion={}",
                username, admin.getTokenVersion());
    }
}

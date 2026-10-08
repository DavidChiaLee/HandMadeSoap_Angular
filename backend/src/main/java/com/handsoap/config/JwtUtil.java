package com.handsoap.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * 簽發 access token（review M8 Phase 1）。
     *
     * <p>{@code tokenVersion} 寫入 {@code tv} claim：logout / 撤銷會把
     * {@code Admin.token_version} +1，{@code JwtAuthFilter} 比對不符即不放行 ——
     * 這是「登出後手上未過期的 token 立即失效」的唯一依據。</p>
     */
    public String generateToken(String username, String role, int tokenVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("tv", tokenVersion)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 讀取 token 的撤銷版本（{@code tv} claim）。
     *
     * <p>回 {@code -1} 表示「沒有這個 claim」—— 兩種情況：簽章錯/過期（解析失敗）
     * 或舊版 token（Phase 1 上線前簽發、無 tv）。兩者都不該放行：
     * DB 的 token_version 從 0 起跳，-1 永遠不相等 → 舊 token 全數失效，
     * 這正是部署 Phase 1 時的預期行為（全員需重新登入一次）。</p>
     */
    public int extractTokenVersion(String token) {
        try {
            Object tv = getClaims(token).get("tv");
            return tv instanceof Number number ? number.intValue() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public String extractUsername(String token) {
        try {
            return getClaims(token).getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateToken(String token, String username) {
        try {
            String extractedUsername = extractUsername(token);
            return extractedUsername.equals(username) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        Date expiration = getClaims(token).getExpiration();
        return expiration.before(new Date());
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 簽發 refresh token（不透明 UUID）。
     * <p>未寫進記憶體或 localStorage，靠 HttpOnly Cookie 丟給瀏覽器；
     * 後端靠 {@link #hashRefreshToken(String)} 存雜湊，所以前端拿不到明文。
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * SHA-256 Hex 雜湊，供 DB 查詢比對。
     * 寫入 DB 的不是明文 refresh token，所以資料庫洩漏也不會直接導致
     * 其他裝置被冒用（每次 login 都會換新 token）。
     */
    public String hashRefreshToken(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("refresh token hash 失敗", e);
        }
    }
}

package com.handsoap.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

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
     * 單次解析後的 token 內容（review M8 Phase 1 + L1）。
     *
     * <p>{@link JwtAuthFilter} 舊寫法會對同一個 token 呼叫
     * {@code extractUsername} → {@code validateToken} → {@code isTokenExpired}
     * → {@code extractTokenVersion}，等於 {@code parseSignedClaims} 跑 4 次、
     * HMAC 驗簽 4 次。現在一律先 {@link #parse(String)} 一次，
     * 各欄位由此 record 衍生。</p>
     *
     * @param username   subject claim
     * @param role       {@code role} claim；舊版 token 可能沒有 → null
     * @param tokenVersion {@code tv} claim；缺此 claim 時為 {@value #NO_TOKEN_VERSION}
     */
    public record ParsedToken(String username, String role, int tokenVersion) {}

    /** {@code tv} claim 缺這的哨兵值：DB 的 token_version 從 0 起跳，-1 永不相等。 */
    public static final int NO_TOKEN_VERSION = -1;

    /**
     * 單次 {@code parseSignedClaims} 取得所有需要的欄位（review L1），
     * 並依例外型別分類記錄（review L2）。
     *
     * <p>回 {@code null} 代表 token 不可信。四類原因的<b>日誌等級刻意不同</b>，
     * 這是 L2 的全部重點 —— 若全部同級，「token 過期」這種每小時都在發生的正常現象
     * 會把「有人偽造 token」的告警淹沒，等於兩者都查不到。</p>
     *
     * <table border="1">
     *   <caption>例外型別 → 日誌等級</caption>
     *   <tr><th>型別</th><th>等級</th><th>理由</th></tr>
     *   <tr><td>{@link ExpiredJwtException}</td><td>debug</td>
     *       <td>正常生命週期（15 分鐘短命 token 到期），前端應觸發 {@code /auth/refresh}</td></tr>
     *   <tr><td>{@link SignatureException}</td><td>warn</td>
     *       <td><b>可能代表攻擊</b>：內容被竄改或憑空偽造</td></tr>
     *   <tr><td>jjwt {@code SecurityException}</td><td>warn</td>
     *       <td>簽章金鑰本身不合規（如 {@code jwt.secret} 不足 256 bit）—— 部署配置錯誤</td></tr>
     *   <tr><td>其餘 {@link JwtException} / {@link IllegalArgumentException}</td><td>debug</td>
     *       <td>token 結構就不是 JWT：客戶端送錯 header 或掃描器亂打</td></tr>
     * </table>
     *
     * <p>過期判斷交給 jjwt：{@code parseSignedClaims} 內部就會驗 {@code exp}
     * claim 並拋 {@code ExpiredJwtException}，因此不必再手動比對
     * {@code getExpiration()}（舊 {@code isTokenExpired} 已移除）。</p>
     */
    public ParsedToken parse(String token) {
        try {
            Claims claims = getClaims(token);
            String username = claims.getSubject();
            if (username == null || username.isBlank()) {
                return null;
            }
            Object role = claims.get("role");
            Object tv = claims.get("tv");
            return new ParsedToken(
                    username,
                    role instanceof String r ? r : null,
                    tv instanceof Number number ? number.intValue() : NO_TOKEN_VERSION
            );
        } catch (ExpiredJwtException e) {
            // 用 debug 而非 warn：閒置後回來是常態，用 warn 會每小時刷一列告警。
            // 記 subject 對「這個人為何突然被登出」的排障很有價值，且不含機密。
            log.debug("[jwt] token 已過期 subject={} exp={}",
                    e.getClaims().getSubject(), e.getClaims().getExpiration());
            return null;
        } catch (SignatureException e) {
            // catch 的是 io.jsonwebtoken.SignatureException（父類別）而非
            // io.jsonwebtoken.security.SignatureException（子類別）：jjwt 0.12.x 有這兩個
            // 同名類別，只 catch 子類會漏掉另一支拋出路徑。
            //
            // L2 唯一對應「可能代表攻擊」的分支。另一個常見成因是金鑰輪替期間
            // 舊 secret 簽發的 token 仍在流通 —— 兩者都值得 warn。
            log.warn("[jwt] 簽章驗證失敗，可能為偽造/竄改的 token 或金鑰輪替未完成：{}",
                    e.getMessage());
            return null;
        } catch (io.jsonwebtoken.security.SecurityException e) {
            // WeakKeyException / InvalidKeyException 的共同父類別（jjwt.secret 過短等）。
            // 必須 warn：若併入下面的 debug，secret 配錯會讓每個請求都失敗卻只剩 debug
            // 紀錄 —— 正是 L2 要避免的「全站靜靜 401」。
            // 全限定名是為了不遮蔽 java.lang.SecurityException。
            log.warn("[jwt] 簽章金鑰不可用，請檢查 jwt.secret 是否為至少 256 bit：{}",
                    e.getMessage());
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            // MalformedJwtException / UnsupportedJwtException：token 結構就不對。
            log.debug("[jwt] token 結構不可用：{}", e.getMessage());
            return null;
        } catch (Exception e) {
            // 理論上不該到這（上面已涵蓋 jjwt 的例外體系）。保留是為了 fail-closed：
            // 寧可記一行 warn，也不要讓例外穿出 filter 把請求打成 500。
            log.warn("[jwt] 解析 token 時發生未預期錯誤", e);
            return null;
        }
    }

    /**
     * 讀取 token 的撤銷版本（{@code tv} claim）。
     *
     * <p>回 {@value #NO_TOKEN_VERSION} 表示「沒有這個 claim」—— 兩種情況：
     * 簽章錯/過期（解析失敗）或舊版 token（Phase 1 上線前簽發、無 tv）。兩者都
     * 不該放行：DB 的 token_version 從 0 起跳，-1 永遠不相等 → 舊 token 全數失效，
     * 這正是部署 Phase 1 時的預期行為（全員需重新登入一次）。</p>
     *
     * <p>保留此便捷方法供舊測試與非 filter 路徑使用；filter 已改走
     * {@link #parse(String)}（單次解析）。</p>
     */
    public int extractTokenVersion(String token) {
        ParsedToken parsed = parse(token);
        return parsed == null ? NO_TOKEN_VERSION : parsed.tokenVersion();
    }

    public String extractUsername(String token) {
        ParsedToken parsed = parse(token);
        return parsed == null ? null : parsed.username();
    }

    public boolean validateToken(String token, String username) {
        ParsedToken parsed = parse(token);
        return parsed != null && parsed.username().equals(username);
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

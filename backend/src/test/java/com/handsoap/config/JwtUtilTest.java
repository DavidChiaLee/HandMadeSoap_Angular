package com.handsoap.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "test_secret_key_for_unit_test_at_least_256_bits_long_0123456789";
    private static final long EXPIRATION_MS = 60_000L;

    private JwtUtil jwtUtil;

    /** L2 測試用：掛在 JwtUtil logger 上擷取事件。 */
    private ListAppender<ILoggingEvent> logAppender;

    /** 掛上 appender 前的原始等級，tearDown 要還原（logger 是 static，會跨測試殘留）。 */
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs", EXPIRATION_MS);
    }

    /**
     * 掛上 ListAppender 並把等級壓到 DEBUG。
     *
     * <p>壓等級是必要的：src/test/resources 沒有任何 logback 設定檔，logback 會退回
     * 預設配置；若 root 為 INFO，{@code log.debug} 事件在抵達 appender 前就被過濾，
     * 斷言會「假通過」（本來就沒事件，anySatisfy 當然找不到東西）。</p>
     */
    private ListAppender<ILoggingEvent> captureLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(JwtUtil.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        return logAppender;
    }

    @AfterEach
    void detachLogAppender() {
        if (logAppender != null) {
            Logger logger = (Logger) LoggerFactory.getLogger(JwtUtil.class);
            logger.detachAppender(logAppender);
            if (originalLevel != null) {
                logger.setLevel(originalLevel);
            }
            logAppender.stop();
            logAppender = null;
        }
    }

    @Test
    @DisplayName("簽發的 token 應可解析出 username，並帶有 role claim")
    void shouldGenerateAndParseToken() {
        String token = jwtUtil.generateToken("admin", "ADMIN", 0);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtUtil.validateToken(token, "admin")).isTrue();
    }

    @Test
    @DisplayName("role claim 應正確寫入 token")
    void shouldStoreRoleClaim() {
        String token = jwtUtil.generateToken("staff", "ADMIN", 0);

        assertThat(secretKey().equals(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("staff");
    }

    @Test
    @DisplayName("parse 應單次解析出 username、role 與 tv（review L1）")
    void shouldParseAllClaimsInOnePass() {
        String token = jwtUtil.generateToken("admin", "ADMIN", 7);

        JwtUtil.ParsedToken parsed = jwtUtil.parse(token);

        assertThat(parsed).isNotNull();
        assertThat(parsed.username()).isEqualTo("admin");
        assertThat(parsed.role()).isEqualTo("ADMIN");
        assertThat(parsed.tokenVersion()).isEqualTo(7);
    }

    @Test
    @DisplayName("tv claim 應帶入 tokenVersion 並可解析（review M8 Phase 1）")
    void shouldStoreTokenVersionClaim() {
        String token = jwtUtil.generateToken("admin", "ADMIN", 7);

        assertThat(jwtUtil.extractTokenVersion(token)).isEqualTo(7);
    }

    @Test
    @DisplayName("無 tv claim 的舊版 token 應解析回 -1（部署 Phase 1 後全數失效，review M8）")
    void shouldReturnMinusOneWhenTokenVersionClaimMissing() {
        String legacyToken = Jwts.builder()
                .subject("admin")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(secretKey())
                .compact();

        assertThat(jwtUtil.extractTokenVersion(legacyToken)).isEqualTo(-1);
        assertThat(jwtUtil.parse(legacyToken).tokenVersion()).isEqualTo(JwtUtil.NO_TOKEN_VERSION);
    }

    @Test
    @DisplayName("role claim 缺這的 token 應解析出 null role（filter 會退回 DB 值）")
    void shouldReturnNullRoleWhenRoleClaimMissing() {
        String tokenWithoutRole = Jwts.builder()
                .subject("admin")
                .claim("tv", 0)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(secretKey())
                .compact();

        assertThat(jwtUtil.parse(tokenWithoutRole).role()).isNull();
    }

    @Test
    @DisplayName("格式非法的 token 解析 tv 應回 -1 而非拋例外")
    void shouldReturnMinusOneForMalformedToken() {
        assertThat(jwtUtil.extractTokenVersion("not-a-jwt")).isEqualTo(-1);
    }

    @Test
    @DisplayName("username 不符時驗證應失敗")
    void shouldFailWhenUsernameMismatch() {
        String token = jwtUtil.generateToken("admin", "ADMIN", 0);

        assertThat(jwtUtil.validateToken(token, "someoneElse")).isFalse();
    }

    @Test
    @DisplayName("過期 token 應驗證失敗")
    void shouldFailForExpiredToken() {
        String expiredToken = buildExpiredToken();

        assertThat(jwtUtil.validateToken(expiredToken, "admin")).isFalse();
    }

    @Test
    @DisplayName("以其他 secret 簽發的 token 應驗證失敗")
    void shouldFailForTokenSignedWithDifferentSecret() {
        String foreignToken = tokenSignedWithDifferentSecret();

        assertThat(jwtUtil.validateToken(foreignToken, "admin")).isFalse();
        assertThat(jwtUtil.extractUsername(foreignToken)).isNull();
    }

    @Test
    @DisplayName("格式非法的 token 應回傳 null 而不是拋出例外")
    void shouldReturnNullForMalformedToken() {
        assertThat(jwtUtil.extractUsername("not-a-jwt")).isNull();
        assertThat(jwtUtil.validateToken("not-a-jwt", "admin")).isFalse();
        assertThat(jwtUtil.parse("not-a-jwt")).isNull();
    }

    @Test
    @DisplayName("過期 token 的 parse 應回 null（jjwt 內部驗 exp）")
    void shouldReturnNullForExpiredTokenInParse() {
        assertThat(jwtUtil.parse(buildExpiredToken())).isNull();
    }

    @Test
    @DisplayName("以其他 secret 簽發的 token parse 應回 null（簽章驗不過）")
    void shouldReturnNullForForeignTokenInParse() {
        assertThat(jwtUtil.parse(tokenSignedWithDifferentSecret())).isNull();
    }

    @Test
    @DisplayName("token 過期時間應依 expiration-ms 設定")
    void shouldUseConfiguredExpiration() {
        String token = jwtUtil.generateToken("admin", "ADMIN", 0);
        Date expiration = Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();

        long remaining = expiration.getTime() - System.currentTimeMillis();
        assertThat(remaining).isLessThanOrEqualTo(EXPIRATION_MS).isPositive();
    }

    @Test
    @DisplayName("過期 token 應記 debug 級日誌（正常生命週期，不該是 warn）（review L2）")
    void shouldLogExpiredTokenAtDebugLevel() {
        ListAppender<ILoggingEvent> appender = captureLogs();

        assertThat(jwtUtil.parse(buildExpiredToken())).isNull();

        // 閒置後回來是常態。若這裡誤用 warn，真人使用者每小時都在刷告警，
        // 會把下面那支「簽章失敗」的真正攻擊訊號淹沒。
        assertThat(appender.list)
                .as("過期屬正常生命週期，應記 DEBUG 且帶出 subject 供排障")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                    assertThat(event.getFormattedMessage()).contains("已過期", "admin");
                });
    }

    @Test
    @DisplayName("簽章驗證失敗應記 warn 級日誌（可能代表偽造攻擊）（review L2）")
    void shouldLogSignatureFailureAtWarnLevel() {
        ListAppender<ILoggingEvent> appender = captureLogs();

        assertThat(jwtUtil.parse(tokenSignedWithDifferentSecret())).isNull();

        // L2 唯一要求 warn 的 token 分支：簽章不符 = 內容被竄改或憑空偽造。
        assertThat(appender.list)
                .as("簽章不符可能代表攻擊，必須是可被 grep 到的 WARN")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getFormattedMessage()).contains("簽章");
                });
    }

    @Test
    @DisplayName("格式非法的 token 應記 debug 而非 warn（客戶端送錯東西，非攻擊）（review L2）")
    void shouldLogMalformedTokenAtDebugLevel() {
        ListAppender<ILoggingEvent> appender = captureLogs();

        assertThat(jwtUtil.parse("not-a-jwt")).isNull();

        // 掃描器亂打 Authorization header 是家常便飯，記 warn 等於自我淹沒。
        assertThat(appender.list)
                .anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.DEBUG));
    }

    /**
     * 用另一把 secret 簽發的合法結構 token —— 簽章一定驗不過。
     *
     * <p>與「格式非法的 token」是兩種失敗：這個結構完全合法（所以不會被
     * {@code MalformedJwtException} 攔下），只有簽章不符。L2 要分辨的正是這兩者。</p>
     */
    private String tokenSignedWithDifferentSecret() {
        return Jwts.builder()
                .subject("admin")
                .claim("role", "ADMIN")
                .claim("tv", 0)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(Keys.hmacShaKeyFor(
                        "another_secret_key_for_unit_test_0123456789_abcdefghij".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String buildExpiredToken() {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject("admin")
                .claim("role", "ADMIN")
                .issuedAt(new Date(now - 10_000L))
                .expiration(new Date(now - 5_000L))
                .signWith(secretKey())
                .compact();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
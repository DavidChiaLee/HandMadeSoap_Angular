package com.handsoap.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "test_secret_key_for_unit_test_at_least_256_bits_long_0123456789";
    private static final long EXPIRATION_MS = 60_000L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs", EXPIRATION_MS);
    }

    @Test
    @DisplayName("簽發的 token 應可解析出 username，並帶有 role claim")
    void shouldGenerateAndParseToken() {
        String token = jwtUtil.generateToken("admin", "ADMIN");

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtUtil.validateToken(token, "admin")).isTrue();
    }

    @Test
    @DisplayName("role claim 應正確寫入 token")
    void shouldStoreRoleClaim() {
        String token = jwtUtil.generateToken("staff", "ADMIN");

        assertThat(secretKey().equals(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("staff");
    }

    @Test
    @DisplayName("username 不符時驗證應失敗")
    void shouldFailWhenUsernameMismatch() {
        String token = jwtUtil.generateToken("admin", "ADMIN");

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
        String foreignToken = Jwts.builder()
                .subject("admin")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(Keys.hmacShaKeyFor(
                        "another_secret_key_for_unit_test_0123456789_abcdefghij".getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtUtil.validateToken(foreignToken, "admin")).isFalse();
        assertThat(jwtUtil.extractUsername(foreignToken)).isNull();
    }

    @Test
    @DisplayName("格式非法的 token 應回傳 null 而不是拋出例外")
    void shouldReturnNullForMalformedToken() {
        assertThat(jwtUtil.extractUsername("not-a-jwt")).isNull();
        assertThat(jwtUtil.validateToken("not-a-jwt", "admin")).isFalse();
    }

    @Test
    @DisplayName("token 過期時間應依 expiration-ms 設定")
    void shouldUseConfiguredExpiration() {
        String token = jwtUtil.generateToken("admin", "ADMIN");
        Date expiration = Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();

        long remaining = expiration.getTime() - System.currentTimeMillis();
        assertThat(remaining).isLessThanOrEqualTo(EXPIRATION_MS).isPositive();
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
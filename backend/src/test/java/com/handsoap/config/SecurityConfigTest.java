package com.handsoap.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig(mock(JwtAuthFilter.class));

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
}
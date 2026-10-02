package com.handsoap.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UploadResourceHeaderFilter} 測試。
 *
 * <p>防護不應依賴單一入口：nginx 的 {@code location ^~ /uploads/} 已送這些標頭，
 * 但本機開發（{@code environment.ts} 指向 {@code http://localhost:8080/uploads/}）
 * 與「日後若把 backend 的 ports 加回來」的路徑都會繞過 nginx。
 */
class UploadResourceHeaderFilterTest {

    private UploadResourceHeaderFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UploadResourceHeaderFilter();
    }

    private MockHttpServletResponse filterThrough(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    @DisplayName("請求 /uploads/** 應帶上 nosniff 與 X-Frame-Options")
    void shouldAddSecurityHeadersToUploads() throws Exception {
        MockHttpServletResponse response = filterThrough("/uploads/abc.png");

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    @DisplayName("請求 /uploads/** 應帶上 default-src 'none'; sandbox 的 CSP")
    void shouldAddContentSecurityPolicyToUploads() throws Exception {
        MockHttpServletResponse response = filterThrough("/uploads/abc.png");

        assertThat(response.getHeader("Content-Security-Policy"))
                .isEqualTo("default-src 'none'; sandbox");
    }

    @Test
    @DisplayName("即使檔案不存在（404）也應帶上標頭")
    void shouldAddHeadersEvenWhenResourceMissing() throws Exception {
        MockHttpServletResponse response = filterThrough("/uploads/does-not-exist.png");

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Content-Security-Policy")).isNotBlank();
    }

    @Test
    @DisplayName("多層路徑的 /uploads/ 也應帶上標頭")
    void shouldAddHeadersForNestedUploadsPath() throws Exception {
        MockHttpServletResponse response = filterThrough("/uploads/2026/09/abc.png");

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("API 路徑不應套用這些標頭（CSP 對 JSON 沒意義且可能干擾）")
    void shouldNotAddHeadersToApiPaths() throws Exception {
        MockHttpServletResponse response = filterThrough("/api/v1/soaps");

        assertThat(response.getHeader("X-Content-Type-Options")).isNull();
        assertThat(response.getHeader("Content-Security-Policy")).isNull();
    }

    @Test
    @DisplayName("非 /uploads/ 的靜態路徑不應套用")
    void shouldNotAddHeadersToOtherPaths() throws Exception {
        MockHttpServletResponse response = filterThrough("/assets/placeholder.svg");

        assertThat(response.getHeader("Content-Security-Policy")).isNull();
    }
}
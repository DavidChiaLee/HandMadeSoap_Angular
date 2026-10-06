package com.handsoap.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TraceIdFilter} 測試。
 *
 * <p>traceId 串起「錯誤回應」與「同請求的日誌行」（review M1）；
 * 其中 {@code X-Request-Id} 是外部輸入，必須驗證格式，
 * 否則日誌可被注入換行偽造紀錄。</p>
 */
class TraceIdFilterTest {

    private TraceIdFilter filter;

    @BeforeEach
    void setUp() {
        MDC.clear();
        filter = new TraceIdFilter();
    }

    @Test
    @DisplayName("無 X-Request-Id 應生成 16 碼 id、回 X-Trace-Id，且請求期間 MDC 可見、結束後清除")
    void shouldGenerateTraceIdWhenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/soaps");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response,
                (req, res) -> mdcDuringRequest.set(MDC.get(TraceIdFilter.MDC_KEY)));

        String traceId = response.getHeader(TraceIdFilter.RESPONSE_HEADER);
        assertThat(traceId).matches("[0-9a-f]{16}");
        assertThat(mdcDuringRequest.get()).isEqualTo(traceId);
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("合法的 X-Request-Id 應被原樣沿用（代理鏈可貫穿同一 id）")
    void shouldReuseValidIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/soaps");
        request.addHeader(TraceIdFilter.REQUEST_HEADER, "gw-ABC-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(TraceIdFilter.RESPONSE_HEADER)).isEqualTo("gw-ABC-123");
    }

    @Test
    @DisplayName("含換行的 X-Request-Id 應被忽略並改生成（防 log injection）")
    void shouldRejectIncomingRequestIdWithIllegalCharacters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/soaps");
        String malicious = "ok\n2026-01-01 ERROR forged";
        request.addHeader(TraceIdFilter.REQUEST_HEADER, malicious);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String traceId = response.getHeader(TraceIdFilter.RESPONSE_HEADER);
        assertThat(traceId).matches("[0-9a-f]{16}");
        assertThat(traceId).isNotEqualTo(malicious);
    }

    @Test
    @DisplayName("超過 32 字元的 X-Request-Id 應被忽略並改生成")
    void shouldRejectOverlongIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/soaps");
        request.addHeader(TraceIdFilter.REQUEST_HEADER, "a".repeat(33));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(TraceIdFilter.RESPONSE_HEADER)).matches("[0-9a-f]{16}");
    }

    @Test
    @DisplayName("應排在 filter 鏈最前（先於 Security 鏈 order=-100，安全日誌才帶 traceId）")
    void shouldRunBeforeSecurityFilterChain() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
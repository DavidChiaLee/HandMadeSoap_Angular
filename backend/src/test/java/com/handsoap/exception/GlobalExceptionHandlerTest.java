package com.handsoap.exception;

import com.handsoap.config.TraceIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ResourceNotFoundException 應回 404 且 success=false")
    void shouldHandleResourceNotFound() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleResourceNotFound(new ResourceNotFoundException("Soap not found with id: 7"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("success", false)
                .containsEntry("message", "Soap not found with id: 7")
                .containsEntry("data", null);
    }

    @Test
    @DisplayName("BadRequestException 應回 400 且保留訊息")
    void shouldHandleBadRequest() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleBadRequest(new BadRequestException("Invalid category ID"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false)
                .containsEntry("message", "Invalid category ID")
                .containsEntry("data", null);
    }

    @Test
    @DisplayName("MethodArgumentNotValidException 應回 400，data 為欄位錯誤對照表")
    void shouldHandleValidationErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "soapRequest");
        bindingResult.addError(new FieldError("soapRequest", "name", "不可為空"));
        bindingResult.addError(new FieldError("soapRequest", "price", "必須大於 0"));

        MethodParameter parameter = new MethodParameter(
                DummyValidationTarget.class.getDeclaredMethod("save", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false)
                .containsEntry("message", "Validation failed");

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().get("data");
        assertThat(errors).containsEntry("name", "不可為空").containsEntry("price", "必須大於 0");
    }

    @Test
    @DisplayName("AuthenticationException 應回 401 並使用固定訊息，不外洩原始例外")
    void shouldHandleAuthenticationException() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleAuthenticationException(new BadCredentialsException("secret-internal-detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("success", false)
                .containsEntry("message", "Invalid username or password")
                .containsEntry("data", null);
        assertThat(response.getBody().get("message").toString()).doesNotContain("secret-internal-detail");
    }

    @Test
    @DisplayName("MaxUploadSizeExceededException 應回 413")
    void shouldHandleMaxUploadSizeExceeded() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(20L * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).containsEntry("success", false)
                .containsEntry("data", null);
        assertThat((String) response.getBody().get("message")).contains("20MB");
    }

    @Test
    @DisplayName("DataIntegrityViolationException 應回 400 並提示欄位過長或重複")
    void shouldHandleDataIntegrityViolation() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException("duplicate key"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false)
                .containsEntry("data", null);
        assertThat((String) response.getBody().get("message")).contains("重複");
    }

    @Test
    @DisplayName("未處理的例外應回 500 固定訊息 + traceId，不外洩原始例外")
    void shouldHandleGenericException() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGenericException(new IllegalStateException("boom /internal/secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("success", false)
                .containsEntry("message", "伺服器錯誤，請稍後再試")
                .containsEntry("data", null);
        assertThat((String) response.getBody().get("message")).doesNotContain("boom");
        assertThat((String) response.getBody().get("traceId")).isNotBlank();
    }

    @Test
    @DisplayName("MDC 有 traceId 時 500 回應應沿用同一個值（回應與日誌才對得上）")
    void shouldReuseTraceIdFromMdc() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-abc-123");
        try {
            ResponseEntity<Map<String, Object>> response =
                    handler.handleGenericException(new IllegalStateException("boom"));

            assertThat(response.getBody()).containsEntry("traceId", "trace-abc-123");
        } finally {
            MDC.remove(TraceIdFilter.MDC_KEY);
        }
    }

    @Test
    @DisplayName("HttpMessageNotReadableException 應回 400 固定訊息，不外洩解析細節")
    void shouldHandleHttpMessageNotReadable() {
        HttpInputMessage stub = new HttpInputMessage() {
            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }

            @Override
            public InputStream getBody() {
                return InputStream.nullInputStream();
            }
        };
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unrecognized field \"secret\"", stub);

        ResponseEntity<Map<String, Object>> response = handler.handleHttpMessageNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false)
                .containsEntry("message", "請求內容格式錯誤")
                .containsEntry("data", null);
        assertThat((String) response.getBody().get("traceId")).isNotBlank();
        assertThat((String) response.getBody().get("message")).doesNotContain("JSON parse error");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException 應回 400，只帶我們自己的參數名")
    void shouldHandleMethodArgumentTypeMismatch() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                DummyValidationTarget.class.getDeclaredMethod("save", String.class), 0);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", parameter,
                new IllegalArgumentException("For input string: \"abc\""));

        ResponseEntity<Map<String, Object>> response = handler.handleMethodArgumentTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "請求參數格式錯誤：id")
                .containsEntry("data", null);
        assertThat((String) response.getBody().get("message")).doesNotContain("java.lang.Long");
    }

    @Test
    @DisplayName("MissingServletRequestParameterException 應回 400 並指出參數名")
    void shouldHandleMissingServletRequestParameter() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("keyword", "String");

        ResponseEntity<Map<String, Object>> response = handler.handleMissingServletRequestParameter(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "缺少必要參數：keyword");
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException 應回 405")
    void shouldHandleHttpRequestMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST");

        ResponseEntity<Map<String, Object>> response = handler.handleHttpRequestMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).containsEntry("message", "不支援的 HTTP 方法");
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException 應回 415，不回顯客戶端 Content-Type")
    void shouldHandleHttpMediaTypeNotSupported() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                "Content type 'application/xml' not supported");

        ResponseEntity<Map<String, Object>> response = handler.handleHttpMediaTypeNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).containsEntry("message", "不支援的 Content-Type");
        assertThat((String) response.getBody().get("message")).doesNotContain("application/xml");
    }

    /** 僅供取得 MethodParameter，模擬 Controller 的方法簽章。 */
    @SuppressWarnings("unused")
    private static final class DummyValidationTarget {
        public void save(String name) {
        }
    }
}
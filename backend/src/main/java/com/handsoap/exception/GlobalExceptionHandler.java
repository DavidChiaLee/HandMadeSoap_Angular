package com.handsoap.exception;

import com.handsoap.config.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", ex.getMessage());
        response.put("data", null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", ex.getMessage());
        response.put("data", null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Validation failed");

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        response.put("data", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Invalid username or password");
        response.put("data", null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * 登入嘗試過多（review H5）→ 429，並附 {@code Retry-After}。
     *
     * <p>必須在 {@link #handleAuthenticationException(AuthenticationException)} 之外
     * 單獨處理：429 與 401 對使用者的意義不同（等待 vs 檢查密碼），
     * 前端需要不同的提示。若誤回 401，使用者會不斷重試而讓封鎖持續延長。</p>
     */
    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyLoginAttempts(TooManyLoginAttemptsException ex) {
        log.warn("[audit] login-throttled: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", ex.getMessage());
        response.put("data", null);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(ex.getRetryAfterSeconds(), 1)))
                .body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("Upload file too large", ex);
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "檔案過大，上限 20MB，請壓縮後再上傳");
        response.put("data", null);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "資料格式不符，請檢查欄位內容是否過長或重複後再試");
        response.put("data", null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Spring MVC 內建解析例外 → 4xx（review M1）。
     *
     * <p>這幾類原本會被 {@link #handleGenericException} 一併吞成 500，除了狀態碼錯誤，
     * 還可能把 Jackson 的欄位結構、Spring 的型別描述帶進回應。實際上都是客戶端的錯，
     * 故回 4xx + 固定（可控）訊息；cause 細節只寫日誌，body 附 traceId 供比對。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {
        String traceId = TraceIdFilter.currentTraceId();
        // cause 含內部欄位與型別結構，僅記日誌
        log.warn("[traceId={}] HttpMessageNotReadableException", traceId, ex);
        return errorResponse(HttpStatus.BAD_REQUEST, "請求內容格式錯誤", traceId);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        String traceId = TraceIdFilter.currentTraceId();
        // 只回我們自己的參數名；requiredType 與傳入值不上送
        log.warn("[traceId={}] Method argument type mismatch: name={}, value={}",
                traceId, ex.getName(), ex.getValue());
        return errorResponse(HttpStatus.BAD_REQUEST, "請求參數格式錯誤：" + ex.getName(), traceId);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex) {
        String traceId = TraceIdFilter.currentTraceId();
        log.warn("[traceId={}] Missing request parameter: {}", traceId, ex.getParameterName());
        return errorResponse(HttpStatus.BAD_REQUEST, "缺少必要參數：" + ex.getParameterName(), traceId);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        String traceId = TraceIdFilter.currentTraceId();
        log.warn("[traceId={}] HTTP method not supported: {}", traceId, ex.getMethod());
        return errorResponse(HttpStatus.METHOD_NOT_ALLOWED, "不支援的 HTTP 方法", traceId);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex) {
        String traceId = TraceIdFilter.currentTraceId();
        // 不回顯客戶端送的 Content-Type：那是外部任意字串
        log.warn("[traceId={}] Unsupported media type: {}", traceId, ex.getContentType());
        return errorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支援的 Content-Type", traceId);
    }

    /**
     * 未分類例外 → 500（review M1）。
     *
     * <p>固定訊息 + traceId：例外細節（SQL 片段、檔案路徑、內部欄位結構）只入日誌，
     * 不再串進 {@code message}。前端把 traceId 附在回報裡，排障時以同一個 id
     * 比對日誌的 {@code [traceId:...]} 即可找到完整堆疊。</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        String traceId = TraceIdFilter.currentTraceId();
        log.error("[traceId={}] Unhandled exception", traceId, ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "伺服器錯誤，請稍後再試", traceId);
    }

    /** 組出含 traceId 的錯誤回應；message 必須是本檔自訂的可控字串，不得拼接例外細節。 */
    private static ResponseEntity<Map<String, Object>> errorResponse(
            HttpStatus status, String message, String traceId) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("data", null);
        response.put("traceId", traceId);
        return ResponseEntity.status(status).body(response);
    }
}

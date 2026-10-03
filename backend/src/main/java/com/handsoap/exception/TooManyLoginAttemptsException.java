package com.handsoap.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 登入嘗試過於頻繁（review H5）。
 *
 * <p>刻意與「帳密錯誤」分成不同的狀態碼：兩者的處置方式完全不同 ——
 * 帳密錯誤時使用者該檢查輸入，嘗試過多時使用者該等待。若都回 401，
 * 使用者會不斷重試，反而讓封鎖一直延長。</p>
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TooManyLoginAttemptsException extends RuntimeException {

    /** 建議等待的秒數，供 {@code Retry-After} 標頭與前端提示使用。 */
    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
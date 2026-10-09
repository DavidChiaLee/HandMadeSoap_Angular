package com.handsoap.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 聯絡表單送出過於頻繁（review 728）→ 429。
 *
 * <p>刻意與「欄位驗證失敗」（400）分開：400 要求使用者修改輸入，
 * 429 要求使用者等待。混在一起會誘導使用者不斷重試，讓封鎖一直延長。</p>
 *
 * <p>{@code @ResponseStatus} 只是兜底（確保未被 GlobalExceptionHandler
 * 攔截時仍為 429）；實際回應由 {@code GlobalExceptionHandler} 附加
 * {@code Retry-After} 標頭。</p>
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TooManyContactSubmissionsException extends RuntimeException {

    /** 建議等待的秒數，供 {@code Retry-After} 標頭與前端提示使用。 */
    private final long retryAfterSeconds;

    public TooManyContactSubmissionsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
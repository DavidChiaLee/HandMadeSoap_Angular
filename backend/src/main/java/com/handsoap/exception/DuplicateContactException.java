package com.handsoap.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 內容重複的聯絡表單（review 728）→ 409。
 *
 * <p>典型來源是使用者連點「送出」或重複貼上相同內容。不寫庫、回 409
 * 並附友善訊息，讓使用者知道「已經收到了」而不是莫名失敗。</p>
 *
 * <p>只比對「同 email + 同主旨 + 同內容 + N 分鐘內」，不會誤擋
 * 使用者稍後提出的另一個問題。</p>
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateContactException extends RuntimeException {

    public DuplicateContactException(String message) {
        super(message);
    }
}
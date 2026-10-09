package com.handsoap.controller;

import com.handsoap.config.ClientIpResolver;
import com.handsoap.dto.ApiResponse;
import com.handsoap.dto.ContactRequest;
import com.handsoap.entity.Contact;
import com.handsoap.exception.TooManyContactSubmissionsException;
import com.handsoap.service.ContactService;
import com.handsoap.service.ContactSubmissionThrottleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * 公開聯絡表單端點（review 728 防灌水）。
 *
 * <p>三道防線（依序）：</p>
 * <ol>
 *   <li>Honeypot：蜜罐欄位非空 → 不寫庫、回 201 通用訊息（silent success）。</li>
 *   <li>每 IP 滑動窗口限流：超過 {@code app.contact.max-per-window} 件 → 429 + Retry-After。</li>
 *   <li>內容去重（於 {@link ContactService}）：N 分鐘內同 email+主旨+內容 → 409。</li>
 * </ol>
 *
 * <p>nginx {@code limit_req} 是第 0 道（擋洪水）；限流檢查放 Controller
 * 而非 Service：與 AuthController 相同，需要 {@link HttpServletRequest}
 * 取得用戶端 IP，而 Service 層不該依賴 servlet API。</p>
 */
@RestController
@RequestMapping("/api/v1/contacts")
public class PublicContactController {

    private static final Logger log = LoggerFactory.getLogger(PublicContactController.class);

    private final ContactService contactService;
    private final ContactSubmissionThrottleService contactThrottle;
    private final ClientIpResolver clientIpResolver;

    public PublicContactController(
            ContactService contactService,
            ContactSubmissionThrottleService contactThrottle,
            ClientIpResolver clientIpResolver) {
        this.contactService = contactService;
        this.contactThrottle = contactThrottle;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Contact>> createContact(
            @Valid @RequestBody ContactRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = clientIpResolver.resolve(httpRequest);

        // ── 防線 1：honeypot ──
        // 非空 = bot 自動填表。不寫庫、仍回 201：讓 bot 以為成功，不會改策略再試；
        // 同時真人在極罕見情況下誤填（例如無障礙工具代填）也不會收到錯誤。
        if (request.getCompany() != null && !request.getCompany().isBlank()) {
            log.warn("[audit] contact-honeypot-tripped ip={} — 視為 bot，未寫入資料庫", clientIp);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Contact submitted successfully", null));
        }

        // ── 防線 2：每 IP 滑動窗口限流 ──
        // 只檢查不計數；計數在下方確定寫入成功後才發生（避免失敗請求污染計數）。
        Duration remaining = contactThrottle.check(clientIp);
        if (remaining.compareTo(Duration.ZERO) > 0) {
            long retryAfter = Math.max(remaining.toSeconds(), 1);
            throw new TooManyContactSubmissionsException(
                    "送出過於頻繁，請於 " + retryAfter + " 秒後再試", retryAfter);
        }

        // ── 防線 3：內容去重（service 層，可能拋 DuplicateContactException → 409）──
        Contact contact = contactService.saveContact(request);

        // 只有真正寫進資料庫才佔額度。
        contactThrottle.recordSuccess(clientIp);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Contact submitted successfully", contact));
    }
}

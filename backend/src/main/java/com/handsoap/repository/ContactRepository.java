package com.handsoap.repository;

import com.handsoap.entity.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    Page<Contact> findByIsReplied(Boolean isReplied, Pageable pageable);

    /**
     * 內容去重（review 728）：同 email + 同主旨 + 同內容，且建立時間晚於 after。
     *
     * <p>email 用 IgnoreCase —— 前端會 trim 但大小寫由使用者輸入，比對時
     * 不應因 {@code A@B.com} vs {@code a@b.com} 判成兩筆。subject/message
     * 為精確比對：稍微改字的「新問題」必須放行，否則會誤擋真人。</p>
     *
     * <p>需搭配 {@code V5__contact_spam_protection.sql} 的
     * (email, created_at) 索引，否則表大時會全表掃。</p>
     */
    boolean existsByEmailIgnoreCaseAndSubjectAndMessageAndCreatedAtAfter(
            String email, String subject, String message, LocalDateTime after);
}

package com.handsoap.service;

import com.handsoap.dto.ContactRequest;
import com.handsoap.entity.Contact;
import com.handsoap.exception.DuplicateContactException;
import com.handsoap.exception.ResourceNotFoundException;
import com.handsoap.repository.ContactRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class ContactService {

    private final ContactRepository contactRepository;

    /** 內容去重窗口：此時間內的完全相同內容視為重複（連點／重複貼上）。 */
    private final int dedupWindowMinutes;

    public ContactService(
            ContactRepository contactRepository,
            @Value("${app.contact.dedup-window-minutes:10}") int dedupWindowMinutes) {
        this.contactRepository = contactRepository;
        this.dedupWindowMinutes = dedupWindowMinutes;
    }

    /**
     * 新增聯絡表單（review 728 防灌水：內容去重）。
     *
     * <p>N 分鐘內同 email + 同主旨 + 同內容 → 拋 {@link DuplicateContactException}
     * （→ 409），<b>不寫庫</b>。這擋的是使用者連點「送出」與重複貼上 ——
     * 這類重複不是 rate limit 能擋的（間隔可能拉很開），也不是 honeypot
     * 能擋的（值是真人填的）。不同內容（哪怕只差一個字）照常放行，
     * 不會誤擋使用者後續提出的另一個問題。</p>
     *
     * <p>注意此方法<b>不檢查</b> honeypot 與 IP 限流 —— 兩者需要
     * {@code HttpServletRequest}，由 {@code PublicContactController} 處理。</p>
     */
    public Contact saveContact(ContactRequest request) {
        if (contactRepository.existsByEmailIgnoreCaseAndSubjectAndMessageAndCreatedAtAfter(
                request.getEmail().trim(),
                request.getSubject(),
                request.getMessage(),
                LocalDateTime.now().minusMinutes(dedupWindowMinutes))) {
            throw new DuplicateContactException("您已送出相同訊息，我們已經收到了，請稍後再試");
        }

        Contact contact = Contact.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .subject(request.getSubject())
                .message(request.getMessage())
                .isReplied(false)
                .build();

        return contactRepository.save(contact);
    }

    public Page<Contact> getAllContacts(Pageable pageable) {
        return contactRepository.findAll(pageable);
    }

    public Page<Contact> getUnrepliedContacts(Pageable pageable) {
        return contactRepository.findByIsReplied(false, pageable);
    }

    public Contact markAsReplied(Long id) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found with id: " + id));
        contact.setIsReplied(true);
        return contactRepository.save(contact);
    }

    /**
     * 更新聯絡資料。以可變欄位覆蓋，保留 id / createdAt 等由系統維護的欄位。
     */
    public Contact updateContact(Long id, ContactRequest request) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found with id: " + id));

        contact.setName(request.getName());
        contact.setEmail(request.getEmail());
        contact.setPhone(request.getPhone());
        contact.setSubject(request.getSubject());
        contact.setMessage(request.getMessage());

        return contactRepository.save(contact);
    }

    /**
     * 切換回覆狀態，讓後台可單獨將某筆訊息標記為已回覆或未回覆。
     */
    public Contact toggleReplied(Long id, boolean isReplied) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found with id: " + id));

        contact.setIsReplied(isReplied);
        return contactRepository.save(contact);
    }

    public void deleteContact(Long id) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found with id: " + id));
        contactRepository.delete(contact);
    }
}

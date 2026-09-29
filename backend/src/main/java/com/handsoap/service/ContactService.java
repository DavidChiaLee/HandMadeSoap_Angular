package com.handsoap.service;

import com.handsoap.dto.ContactRequest;
import com.handsoap.entity.Contact;
import com.handsoap.exception.ResourceNotFoundException;
import com.handsoap.repository.ContactRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ContactService {

    private final ContactRepository contactRepository;

    public ContactService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    public Contact saveContact(ContactRequest request) {
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

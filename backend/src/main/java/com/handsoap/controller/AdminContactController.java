package com.handsoap.controller;

import com.handsoap.dto.ApiResponse;
import com.handsoap.dto.ContactRequest;
import com.handsoap.entity.Contact;
import com.handsoap.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/contacts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminContactController {

    private final ContactService contactService;

    public AdminContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Contact>>> getAllContacts(Pageable pageable) {
        Page<Contact> contacts = contactService.getAllContacts(pageable);
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    @GetMapping("/unreplied")
    public ResponseEntity<ApiResponse<Page<Contact>>> getUnrepliedContacts(Pageable pageable) {
        Page<Contact> contacts = contactService.getUnrepliedContacts(pageable);
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    @PutMapping("/{id}/replied")
    public ResponseEntity<ApiResponse<Contact>> markAsReplied(@PathVariable Long id) {
        Contact contact = contactService.markAsReplied(id);
        return ResponseEntity.ok(ApiResponse.success("Contact marked as replied", contact));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Contact>> updateContact(
            @PathVariable Long id,
            @Valid @RequestBody ContactRequest request) {
        Contact contact = contactService.updateContact(id, request);
        return ResponseEntity.ok(ApiResponse.success("Contact updated successfully", contact));
    }

    /**
     * 切換回覆狀態。前端「標記已回覆／標記未回覆」按鈕會送出反轉後的 isReplied，
     * 避免前端送整筆 Contact（含 id、createdAt）造成欄位耦合。
     */
    @PutMapping("/{id}/replied-status")
    public ResponseEntity<ApiResponse<Contact>> toggleReplied(
            @PathVariable Long id,
            @RequestParam("isReplied") boolean isReplied) {
        Contact contact = contactService.toggleReplied(id, isReplied);
        return ResponseEntity.ok(ApiResponse.success("Contact replied status updated", contact));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteContact(@PathVariable Long id) {
        contactService.deleteContact(id);
        return ResponseEntity.ok(ApiResponse.success("Contact deleted successfully", null));
    }
}

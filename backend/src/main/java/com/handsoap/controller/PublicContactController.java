package com.handsoap.controller;

import com.handsoap.dto.ApiResponse;
import com.handsoap.dto.ContactRequest;
import com.handsoap.entity.Contact;
import com.handsoap.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contacts")
public class PublicContactController {

    private final ContactService contactService;

    public PublicContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Contact>> createContact(@Valid @RequestBody ContactRequest request) {
        Contact contact = contactService.saveContact(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Contact submitted successfully", contact));
    }
}

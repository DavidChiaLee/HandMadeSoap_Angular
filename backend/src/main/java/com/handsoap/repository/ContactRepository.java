package com.handsoap.repository;

import com.handsoap.entity.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    Page<Contact> findByIsReplied(Boolean isReplied, Pageable pageable);
}

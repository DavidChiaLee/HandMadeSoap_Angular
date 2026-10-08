package com.handsoap.repository;

import com.handsoap.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    /** <<< 新增：用 refresh token 雜湊值找出對應 admin */
    Optional<Admin> findByRefreshTokenHash(String refreshTokenHash);
}

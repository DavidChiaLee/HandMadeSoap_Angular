package com.handsoap.service;

import com.handsoap.dto.AdminResponse;
import com.handsoap.dto.CreateAdminRequest;
import com.handsoap.entity.Admin;
import com.handsoap.exception.BadRequestException;
import com.handsoap.repository.AdminRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 建立管理員帳號。
     *
     * <p>首次啟動時（admins 表為空）任何人都可以建立第一個管理員作為 bootstrap；
     * 一旦已有管理員，則必須由已登入的 ADMIN 才能再新增。</p>
     *
     * @param request 建立管理員的請求
     * @param isAdmin 目前是否已以 ADMIN 身分登入
     * @return 建立成功的管理員資料
     */
    public AdminResponse createAdmin(CreateAdminRequest request, boolean isAdmin) {
        long adminCount = adminRepository.count();
        if (adminCount > 0 && !isAdmin) {
            throw new BadRequestException(
                    "Admins already exist. Please log in as an ADMIN to create additional administrators."
            );
        }

        if (adminRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already exists: " + request.getUsername());
        }

        if (adminRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already exists: " + request.getEmail());
        }

        String role = (request.getRole() == null || request.getRole().isBlank())
                ? "ADMIN"
                : request.getRole().trim().toUpperCase();

        if (!"ADMIN".equals(role)) {
            throw new BadRequestException("Role must be ADMIN");
        }

        Admin admin = Admin.builder()
                .username(request.getUsername().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail().trim())
                .role(role)
                .build();

        Admin saved = adminRepository.save(admin);
        return AdminResponse.fromEntity(saved);
    }
}
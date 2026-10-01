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
     * <p>呼叫端必須已是以 ADMIN 身分登入 —— 授權由 SecurityConfig 的
     * {@code /api/v1/admin/**} → {@code hasRole("ADMIN")} 保證，Service 層不再重複判斷。</p>
     *
     * <p>首位管理員不走這裡，而是由啟動時的
     * {@link com.handsoap.config.AdminBootstrapRunner} 建立。</p>
     *
     * <p>註：舊版這裡有 {@code adminRepository.count() > 0 && !isAdmin} 的檢查，
     * 那是 TOCTOU 競態的根源（count 與 save 非原子），且該端點在 SecurityConfig
     * 是 permitAll，等於把部署後的時間窗開放給公網搶註 ADMIN。兩者皆已移除。</p>
     *
     * @param request 建立管理員的請求
     * @return 建立成功的管理員資料
     */
    public AdminResponse createAdmin(CreateAdminRequest request) {
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
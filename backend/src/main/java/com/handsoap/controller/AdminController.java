package com.handsoap.controller;

import com.handsoap.dto.AdminResponse;
import com.handsoap.dto.ApiResponse;
import com.handsoap.dto.CreateAdminRequest;
import com.handsoap.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理員帳號管理。
 *
 * <p>權限由 SecurityConfig 的 {@code /api/v1/admin/**} → {@code hasRole("ADMIN")} 統一控管，
 * Controller 不再做重複的身分判斷。首位管理員改由啟動時的
 * {@link com.handsoap.config.AdminBootstrapRunner} 建立，不再透過 HTTP 匿名建立。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/admins")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminResponse>> createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        AdminResponse admin = adminService.createAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Admin created successfully", admin));
    }
}
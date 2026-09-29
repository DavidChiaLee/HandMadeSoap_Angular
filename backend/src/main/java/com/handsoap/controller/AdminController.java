package com.handsoap.controller;

import com.handsoap.dto.AdminResponse;
import com.handsoap.dto.ApiResponse;
import com.handsoap.dto.CreateAdminRequest;
import com.handsoap.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 管理員帳號管理。
 *
 * <p>POST /api/v1/admin/admins 在 admins 表為空時可用於初始化第一個管理員（bootstrap）；
 * 一旦已有管理員，僅允許 ADMIN 角色呼叫。</p>
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> Objects.equals(authority.getAuthority(), "ROLE_ADMIN"));

        AdminResponse admin = adminService.createAdmin(request, isAdmin);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Admin created successfully", admin));
    }
}
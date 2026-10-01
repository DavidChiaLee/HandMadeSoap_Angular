package com.handsoap.controller;

import com.handsoap.config.SecurityConfig;
import com.handsoap.dto.AdminResponse;
import com.handsoap.dto.CreateAdminRequest;
import com.handsoap.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminController 的授權行為。
 *
 * <p>review C4 修正後，{@code POST /api/v1/admin/admins} 不再 permitAll ——
 * 首位管理員改由啟動時的 {@code AdminBootstrapRunner} 建立。
 * 這裡驗證「未登入一律被擋」，防止日後有人又把 permitAll 加回去。</p>
 */
@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, ControllerTestSupport.class})
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @Test
    @DisplayName("未登入不得建立管理員（bootstrap 端點已移除，應回 401）")
    void shouldRejectAnonymousCreateAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"first\",\"password\":\"secret\",\"email\":\"a@b.com\"}"))
                .andExpect(status().isUnauthorized());

        verify(adminService, never()).createAdmin(any());
    }

    @Test
    @DisplayName("以 ADMIN 身分建立管理員應成功")
    @WithMockUser(roles = "ADMIN")
    void shouldCreateAdminWhenAuthenticatedAsAdmin() throws Exception {
        given(adminService.createAdmin(any(CreateAdminRequest.class)))
                .willReturn(AdminResponse.builder().id(2L).username("second").role("ADMIN").build());

        mockMvc.perform(post("/api/v1/admin/admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"second\",\"password\":\"secret\",\"email\":\"c@d.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("second"));

        verify(adminService).createAdmin(any(CreateAdminRequest.class));
    }

    @Test
    @DisplayName("非 ADMIN 身分不得建立管理員（應回 403）")
    @WithMockUser(roles = "USER")
    void shouldRejectNonAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"secret\",\"email\":\"x@y.com\"}"))
                .andExpect(status().isForbidden());

        verify(adminService, never()).createAdmin(any());
    }

    @Test
    @DisplayName("欄位驗證失敗時應回 400")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400OnInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/admin/admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }
}
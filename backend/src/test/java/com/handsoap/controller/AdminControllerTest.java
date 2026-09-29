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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, ControllerTestSupport.class})
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @Test
    @DisplayName("未登入（bootstrap）建立管理員時應傳入 isAdmin=false")
    void shouldPassIsAdminFalseWhenAnonymous() throws Exception {
        given(adminService.createAdmin(any(CreateAdminRequest.class), eq(false)))
                .willReturn(AdminResponse.builder().id(1L).username("first").role("ADMIN").build());

        mockMvc.perform(post("/api/v1/admin/admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"first\",\"password\":\"secret\",\"email\":\"a@b.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("first"));

        verify(adminService).createAdmin(any(CreateAdminRequest.class), eq(false));
    }

    @Test
    @DisplayName("以 ADMIN 身分建立管理員時應傳入 isAdmin=true")
    @WithMockUser(roles = "ADMIN")
    void shouldPassIsAdminTrueWhenAdmin() throws Exception {
        given(adminService.createAdmin(any(CreateAdminRequest.class), eq(true)))
                .willReturn(AdminResponse.builder().id(2L).username("second").role("ADMIN").build());

        mockMvc.perform(post("/api/v1/admin/admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"second\",\"password\":\"secret\",\"email\":\"c@d.com\"}"))
                .andExpect(status().isCreated());

        verify(adminService).createAdmin(any(CreateAdminRequest.class), eq(true));
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
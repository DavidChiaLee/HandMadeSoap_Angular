package com.handsoap.controller;

import com.handsoap.config.SecurityConfig;
import com.handsoap.dto.CategoryResponse;
import com.handsoap.service.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminCategoryController.class)
@Import({SecurityConfig.class, ControllerTestSupport.class})
class AdminCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @Test
    @DisplayName("應回傳分類清單")
    @WithMockUser(roles = "ADMIN")
    void shouldReturnCategories() throws Exception {
        given(categoryService.getAllCategories(any()))
                .willReturn(new PageImpl<>(List.of(CategoryResponse.builder().id(1L).name("一般皂").build())));

        mockMvc.perform(get("/api/v1/admin/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("一般皂"));
    }

    @Test
    @DisplayName("依 id 查詢應回傳該筆資料")
    @WithMockUser(roles = "ADMIN")
    void shouldReturnCategoryById() throws Exception {
        given(categoryService.getCategoryById(1L))
                .willReturn(CategoryResponse.builder().id(1L).name("一般皂").build());

        mockMvc.perform(get("/api/v1/admin/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("一般皂"));
    }

    @Test
    @DisplayName("刪除應正確呼叫 service")
    @WithMockUser(roles = "ADMIN")
    void shouldDeleteCategory() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/categories/1")).andExpect(status().isOk());

        verify(categoryService).deleteCategory(1L);
    }

    @Test
    @DisplayName("未登入時應回 401")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("非 ADMIN 角色時應回 403")
    @WithMockUser(roles = "USER")
    void shouldReturn403WhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/categories")).andExpect(status().isForbidden());
    }
}
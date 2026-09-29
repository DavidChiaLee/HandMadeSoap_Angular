package com.handsoap.controller;

import com.handsoap.config.SecurityConfig;
import com.handsoap.dto.CategoryResponse;
import com.handsoap.dto.ContactRequest;
import com.handsoap.entity.Contact;
import com.handsoap.service.CategoryService;
import com.handsoap.service.ContactService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PublicCategoryController.class, PublicContactController.class})
@Import({SecurityConfig.class, ControllerTestSupport.class})
class PublicCategoryContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private ContactService contactService;

    @Test
    @DisplayName("前台分類清單不需登入即可取得")
    void shouldReturnCategoriesWithoutAuthentication() throws Exception {
        given(categoryService.getAllCategories(any()))
                .willReturn(new PageImpl<>(List.of(CategoryResponse.builder().id(1L).name("一般皂").build())));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("一般皂"));
    }

    @Test
    @DisplayName("前台分類依 id 查詢應回傳該筆資料")
    void shouldReturnCategoryById() throws Exception {
        given(categoryService.getCategoryById(1L))
                .willReturn(CategoryResponse.builder().id(1L).name("一般皂").build());

        mockMvc.perform(get("/api/v1/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("一般皂"));
    }

    @Test
    @DisplayName("前台送出聯絡表單不需登入即可建立")
    void shouldCreateContactWithoutAuthentication() throws Exception {
        given(contactService.saveContact(any(ContactRequest.class)))
                .willReturn(Contact.builder().id(1L).name("王小明").build());

        mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"王小明\",\"email\":\"a@b.com\",\"subject\":\"問題\",\"message\":\"內容\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("王小明"));

        verify(contactService).saveContact(any(ContactRequest.class));
    }

    @Test
    @DisplayName("聯絡表單欄位驗證失敗時應回 400")
    void shouldReturn400OnInvalidContact() throws Exception {
        mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"bad-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.name").exists());
    }
}
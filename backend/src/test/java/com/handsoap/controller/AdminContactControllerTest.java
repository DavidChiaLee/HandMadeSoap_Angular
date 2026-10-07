package com.handsoap.controller;

import com.handsoap.config.SecurityConfig;
import com.handsoap.entity.Contact;
import com.handsoap.exception.ResourceNotFoundException;
import com.handsoap.service.ContactService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminContactController.class)
@Import({SecurityConfig.class, ControllerTestSupport.class})
class AdminContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContactService contactService;

    /** 符合 ContactRequest Bean Validation 的請求內容 */
    private static final String validRequestJson = """
            {
              "name": "李大華",
              "email": "lee@example.com",
              "phone": "0987654321",
              "subject": "訂單問題",
              "message": "請問出貨時間"
            }
            """;

    @Test
    @DisplayName("應回傳聯絡清單並套用分頁與 createdAt DESC 排序")
    @WithMockUser(roles = "ADMIN")
    void shouldReturnAllContactsWithDefaultPaging() throws Exception {
        given(contactService.getAllContacts(any()))
                .willReturn(new PageImpl<>(List.of(Contact.builder().id(1L).name("王小明").build())));

        mockMvc.perform(get("/api/v1/admin/contacts")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("王小明"));

        verify(contactService).getAllContacts(any());
    }

    @Test
    @DisplayName("未傳入分頁參數時仍應提供預設 createdAt DESC 分頁")
    @WithMockUser(roles = "ADMIN")
    void shouldApplyDefaultSortingAndPagingWhenNoPageableArgs() throws Exception {
        given(contactService.getAllContacts(any()))
                .willReturn(new PageImpl<>(List.of(Contact.builder().id(1L).name("王小明").build())));

        mockMvc.perform(get("/api/v1/admin/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("王小明"));

        verify(contactService).getAllContacts(any());
    }

    @Test
    @DisplayName("未回覆清單應呼叫 getUnrepliedContacts")
    @WithMockUser(roles = "ADMIN")
    void shouldReturnUnrepliedContacts() throws Exception {
        given(contactService.getUnrepliedContacts(any()))
                .willReturn(new PageImpl<>(List.of(Contact.builder().id(2L).name("李大華").isReplied(false).build())));

        mockMvc.perform(get("/api/v1/admin/contacts/unreplied"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("李大華"));
    }

    @Test
    @DisplayName("標記已回覆應呼叫 markAsReplied")
    @WithMockUser(roles = "ADMIN")
    void shouldMarkAsReplied() throws Exception {
        given(contactService.markAsReplied(1L))
                .willReturn(Contact.builder().id(1L).name("王小明").isReplied(true).build());

        mockMvc.perform(put("/api/v1/admin/contacts/1/replied"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isReplied").value(true));

        verify(contactService).markAsReplied(1L);
    }

    @Test
    @DisplayName("更新聯絡資料應呼叫 updateContact 並回傳更新後內容")
    @WithMockUser(roles = "ADMIN")
    void shouldUpdateContact() throws Exception {
        given(contactService.updateContact(eq(1L), any()))
                .willReturn(Contact.builder().id(1L).name("李大華").isReplied(false).build());

        mockMvc.perform(put("/api/v1/admin/contacts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("李大華"));

        verify(contactService).updateContact(eq(1L), any());
    }

    @Test
    @DisplayName("更新資料時欄位驗證失敗應回 400")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400WhenUpdateValidationFails() throws Exception {
        mockMvc.perform(put("/api/v1/admin/contacts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.name").exists())
                .andExpect(jsonPath("$.data.email").exists());
    }

    @Test
    @DisplayName("切換回覆狀態應呼叫 toggleReplied")
    @WithMockUser(roles = "ADMIN")
    void shouldToggleRepliedStatus() throws Exception {
        given(contactService.toggleReplied(1L, true))
                .willReturn(Contact.builder().id(1L).isReplied(true).build());

        mockMvc.perform(put("/api/v1/admin/contacts/1/replied-status").param("isReplied", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isReplied").value(true));

        verify(contactService).toggleReplied(1L, true);
    }

    @Test
    @DisplayName("刪除聯絡資料應回 200 並呼叫 deleteContact")
    @WithMockUser(roles = "ADMIN")
    void shouldDeleteContact() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/contacts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(contactService).deleteContact(1L);
    }

    @Test
    @DisplayName("刪除不存在的資料時應回 404")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn404WhenDeletingMissingContact() throws Exception {
        willThrow(new ResourceNotFoundException("Contact not found with id: 99"))
                .given(contactService).deleteContact(99L);

        mockMvc.perform(delete("/api/v1/admin/contacts/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("未登入時應回 401")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/contacts")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("非 ADMIN 角色時應回 403")
    @WithMockUser(roles = "USER")
    void shouldReturn403WhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/contacts")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("刪除端點未登入時應回 401")
    void shouldReturn401WhenDeletingUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/contacts/1")).andExpect(status().isUnauthorized());
    }
}
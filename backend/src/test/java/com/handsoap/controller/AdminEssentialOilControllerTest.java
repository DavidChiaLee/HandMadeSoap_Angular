package com.handsoap.controller;

import com.handsoap.config.SecurityConfig;
import com.handsoap.dto.EssentialOilRequest;
import com.handsoap.dto.EssentialOilResponse;
import com.handsoap.service.EssentialOilService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminEssentialOilController.class)
@Import({SecurityConfig.class, ControllerTestSupport.class})
class AdminEssentialOilControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EssentialOilService essentialOilService;

    private static final Pageable PAGE = PageRequest.of(0, 5);

    @Test
    @DisplayName("未帶 keyword 時應呼叫 getAllEssentialOils")
    @WithMockUser(roles = "ADMIN")
    void shouldReturnAllWhenKeywordAbsent() throws Exception {
        given(essentialOilService.getAllEssentialOils(any()))
                .willReturn(pageOf(oil(1L, "薰衣草精油")));

        mockMvc.perform(get("/api/v1/admin/essential-oils").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("薰衣草精油"));

        verify(essentialOilService).getAllEssentialOils(any());
        verify(essentialOilService, never()).searchEssentialOils(anyString(), any());
    }

    @Test
    @DisplayName("keyword 只有空白時應視為沒有搜尋條件")
    @WithMockUser(roles = "ADMIN")
    void shouldTreatBlankKeywordAsAbsent() throws Exception {
        given(essentialOilService.getAllEssentialOils(any()))
                .willReturn(pageOf(oil(1L, "檸檬精油")));

        mockMvc.perform(get("/api/v1/admin/essential-oils").param("keyword", "   "))
                .andExpect(status().isOk());

        verify(essentialOilService, never()).searchEssentialOils(anyString(), any());
    }

    @Test
    @DisplayName("帶 keyword 時應呼叫 searchEssentialOils 並回傳結果")
    @WithMockUser(roles = "ADMIN")
    void shouldSearchWhenKeywordPresent() throws Exception {
        given(essentialOilService.searchEssentialOils(eq("薰衣草"), any()))
                .willReturn(pageOf(oil(2L, "薰衣草精油")));

        mockMvc.perform(get("/api/v1/admin/essential-oils").param("keyword", "薰衣草"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("薰衣草精油"));

        verify(essentialOilService).searchEssentialOils(eq("薰衣草"), any());
        verify(essentialOilService, never()).getAllEssentialOils(any());
    }

    @Test
    @DisplayName("搜尋無結果時應回傳空內容")
    @WithMockUser(roles = "ADMIN")
    void shouldReturnEmptyContentWhenNoMatch() throws Exception {
        given(essentialOilService.searchEssentialOils(eq("zzz"), any()))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/admin/essential-oils").param("keyword", "zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    @DisplayName("依 id 查詢應回傳 200 與該筆資料")
    @WithMockUser(roles = "ADMIN")
    void shouldReturnOilById() throws Exception {
        given(essentialOilService.getEssentialOilById(1L)).willReturn(oil(1L, "茶樹精油"));

        mockMvc.perform(get("/api/v1/admin/essential-oils/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("茶樹精油"));
    }

    @Test
    @DisplayName("新增成功時應回傳 201")
    @WithMockUser(roles = "ADMIN")
    void shouldCreateOil() throws Exception {
        given(essentialOilService.createEssentialOil(any(EssentialOilRequest.class)))
                .willReturn(oil(5L, "新精油"));

        mockMvc.perform(post("/api/v1/admin/essential-oils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新精油\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("新精油"));
    }

    @Test
    @DisplayName("EssentialOilRequest 未定義驗證註解，空 payload 仍會建立成功（記錄目前行為）")
    @WithMockUser(roles = "ADMIN")
    void shouldCreateEvenWithEmptyPayloadSinceNoValidationConstraints() throws Exception {
        given(essentialOilService.createEssentialOil(any(EssentialOilRequest.class)))
                .willReturn(oil(6L, "空內容"));

        mockMvc.perform(post("/api/v1/admin/essential-oils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());

        verify(essentialOilService).createEssentialOil(any(EssentialOilRequest.class));
    }

    @Test
    @DisplayName("更新成功時應回傳 200")
    @WithMockUser(roles = "ADMIN")
    void shouldUpdateOil() throws Exception {
        given(essentialOilService.updateEssentialOil(eq(1L), any(EssentialOilRequest.class)))
                .willReturn(oil(1L, "改名後"));

        mockMvc.perform(put("/api/v1/admin/essential-oils/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名後\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名後"));
    }

    @Test
    @DisplayName("刪除成功時應回傳 200")
    @WithMockUser(roles = "ADMIN")
    void shouldDeleteOil() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/essential-oils/1"))
                .andExpect(status().isOk());

        verify(essentialOilService).deleteEssentialOil(1L);
    }

    @Test
    @DisplayName("未登入時應回 401")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/essential-oils"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("非 ADMIN 角色時應回 403")
    @WithMockUser(roles = "USER")
    void shouldReturn403WhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/essential-oils"))
                .andExpect(status().isForbidden());
    }

    private static PageImpl<EssentialOilResponse> pageOf(EssentialOilResponse... items) {
        return new PageImpl<>(List.of(items));
    }

    private static EssentialOilResponse oil(Long id, String name) {
        return EssentialOilResponse.builder().id(id).name(name).isActive(true).build();
    }
}
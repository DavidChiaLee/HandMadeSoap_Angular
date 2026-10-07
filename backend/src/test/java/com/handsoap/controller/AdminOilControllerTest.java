package com.handsoap.controller;

import com.handsoap.config.SecurityConfig;
import com.handsoap.dto.OilRequest;
import com.handsoap.dto.OilResponse;
import com.handsoap.service.OilService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminOilController.class)
@Import({SecurityConfig.class, ControllerTestSupport.class})
class AdminOilControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OilService oilService;

    @Test
    @DisplayName("未帶 keyword 時應呼叫 getAllOils")
    @WithMockUser(roles = "ADMIN")
    void shouldReturnAllWhenKeywordAbsent() throws Exception {
        given(oilService.getAllOils(any())).willReturn(pageOf(oil(1L, "橄欖油")));

        mockMvc.perform(get("/api/v1/admin/oils"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("橄欖油"));

        verify(oilService).getAllOils(any());
        verify(oilService, never()).searchOilsByName(anyString(), any());
    }

    @Test
    @DisplayName("keyword 為空白時應視為沒有搜尋條件")
    @WithMockUser(roles = "ADMIN")
    void shouldTreatBlankKeywordAsAbsent() throws Exception {
        given(oilService.getAllOils(any())).willReturn(pageOf(oil(1L, "橄欖油")));

        mockMvc.perform(get("/api/v1/admin/oils").param("keyword", "   "))
                .andExpect(status().isOk());

        verify(oilService, never()).searchOilsByName(anyString(), any());
    }

    @Test
    @DisplayName("帶 keyword 時應呼叫 searchOilsByName")
    @WithMockUser(roles = "ADMIN")
    void shouldSearchWhenKeywordPresent() throws Exception {
        given(oilService.searchOilsByName(eq("橄欖"), any())).willReturn(pageOf(oil(2L, "橄欖油")));

        mockMvc.perform(get("/api/v1/admin/oils").param("keyword", "橄欖"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("橄欖油"));

        verify(oilService).searchOilsByName(eq("橄欖"), any());
        verify(oilService, never()).getAllOils(any());
    }

    @Test
    @DisplayName("名稱為空時應回 400 並帶欄位錯誤")
    @WithMockUser(roles = "ADMIN")
    void shouldRejectBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/admin/oils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.name").exists());
    }

    @Test
    @DisplayName("新增成功時應回 201")
    @WithMockUser(roles = "ADMIN")
    void shouldCreateOil() throws Exception {
        given(oilService.createOil(any(OilRequest.class))).willReturn(oil(5L, "新油品"));

        mockMvc.perform(post("/api/v1/admin/oils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新油品\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("依 id 查詢應回傳該筆資料")
    @WithMockUser(roles = "ADMIN")
    void shouldReturnOilById() throws Exception {
        given(oilService.getOilById(1L)).willReturn(oil(1L, "橄欖油"));

        mockMvc.perform(get("/api/v1/admin/oils/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("橄欖油"));
    }

    @Test
    @DisplayName("更新與刪除應正確呼叫 service")
    @WithMockUser(roles = "ADMIN")
    void shouldUpdateAndDeleteOil() throws Exception {
        given(oilService.updateOil(eq(1L), any(OilRequest.class))).willReturn(oil(1L, "改名後"));

        mockMvc.perform(put("/api/v1/admin/oils/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名後\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/admin/oils/1"))
                .andExpect(status().isOk());

        verify(oilService).deleteOil(1L);
    }

    @Test
    @DisplayName("刪除單張圖片應呼叫 service 並回 200")
    @WithMockUser(roles = "ADMIN")
    void shouldDeleteOilImage() throws Exception {
        given(oilService.deleteOilImage(1L, 7L)).willReturn(oil(1L, "橄欖油"));

        mockMvc.perform(delete("/api/v1/admin/oils/1/images/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(oilService).deleteOilImage(1L, 7L);
    }

    @Test
    @DisplayName("刪除單張圖片：圖片不屬於該油品時應回 404")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn404WhenImageMissing() throws Exception {
        given(oilService.deleteOilImage(1L, 99L))
                .willThrow(new com.handsoap.exception.ResourceNotFoundException(
                        "Image not found with id: 99 for oil id: 1"));

        mockMvc.perform(delete("/api/v1/admin/oils/1/images/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("未登入時應回 401")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/oils")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("非 ADMIN 角色時應回 403")
    @WithMockUser(roles = "USER")
    void shouldReturn403WhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/oils")).andExpect(status().isForbidden());
    }

    private static PageImpl<OilResponse> pageOf(OilResponse... items) {
        return new PageImpl<>(List.of(items));
    }

    private static OilResponse oil(Long id, String name) {
        return OilResponse.builder().id(id).name(name).isActive(true).build();
    }
}
package com.handsoap.controller;

import com.handsoap.config.SecurityConfig;
import com.handsoap.dto.SoapRequest;
import com.handsoap.dto.SoapResponse;
import com.handsoap.service.SoapService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminSoapController.class)
@Import({SecurityConfig.class, ControllerTestSupport.class})
class AdminSoapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SoapService soapService;

    @Test
    @DisplayName("未帶 keyword 時應呼叫 getAllSoaps")
    @WithMockUser(roles = "ADMIN")
    void shouldReturnAllWhenKeywordAbsent() throws Exception {
        given(soapService.getAllSoaps(any())).willReturn(pageOf(soap(1L, "薰衣草皂")));

        mockMvc.perform(get("/api/v1/admin/soaps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("薰衣草皂"));

        verify(soapService).getAllSoaps(any());
        verify(soapService, never()).searchSoapsByName(anyString(), any());
    }

    @Test
    @DisplayName("keyword 為空白時應視為沒有搜尋條件")
    @WithMockUser(roles = "ADMIN")
    void shouldTreatBlankKeywordAsAbsent() throws Exception {
        given(soapService.getAllSoaps(any())).willReturn(pageOf(soap(1L, "薰衣草皂")));

        mockMvc.perform(get("/api/v1/admin/soaps").param("keyword", " "))
                .andExpect(status().isOk());

        verify(soapService, never()).searchSoapsByName(anyString(), any());
    }

    @Test
    @DisplayName("帶 keyword 時應呼叫 searchSoapsByName")
    @WithMockUser(roles = "ADMIN")
    void shouldSearchWhenKeywordPresent() throws Exception {
        given(soapService.searchSoapsByName(eq("薰衣草"), any())).willReturn(pageOf(soap(2L, "薰衣草皂")));

        mockMvc.perform(get("/api/v1/admin/soaps").param("keyword", "薰衣草"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("薰衣草皂"));

        verify(soapService).searchSoapsByName(eq("薰衣草"), any());
        verify(soapService, never()).getAllSoaps(any());
    }

    @Test
    @DisplayName("新增成功時應回 201")
    @WithMockUser(roles = "ADMIN")
    void shouldCreateSoap() throws Exception {
        given(soapService.createSoap(any(SoapRequest.class))).willReturn(soap(5L, "新皂"));

        mockMvc.perform(post("/api/v1/admin/soaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新皂\",\"categoryId\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("依 id 查詢應回傳該筆資料")
    @WithMockUser(roles = "ADMIN")
    void shouldReturnSoapById() throws Exception {
        given(soapService.getSoapById(1L)).willReturn(soap(1L, "薰衣草皂"));

        mockMvc.perform(get("/api/v1/admin/soaps/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("薰衣草皂"));
    }

    @Test
    @DisplayName("更新與刪除應正確呼叫 service")
    @WithMockUser(roles = "ADMIN")
    void shouldUpdateAndDeleteSoap() throws Exception {
        given(soapService.updateSoap(eq(1L), any(SoapRequest.class))).willReturn(soap(1L, "改名後"));

        mockMvc.perform(put("/api/v1/admin/soaps/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名後\",\"categoryId\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/admin/soaps/1")).andExpect(status().isOk());

        verify(soapService).deleteSoap(1L);
    }

    @Test
    @DisplayName("刪除單張圖片應呼叫 service 並回 200")
    @WithMockUser(roles = "ADMIN")
    void shouldDeleteSoapImage() throws Exception {
        given(soapService.deleteSoapImage(1L, 7L)).willReturn(soap(1L, "薰衣草皂"));

        mockMvc.perform(delete("/api/v1/admin/soaps/1/images/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("薰衣草皂"));

        verify(soapService).deleteSoapImage(1L, 7L);
    }

    @Test
    @DisplayName("刪除單張圖片：圖片不屬於該商品時應回 404")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn404WhenImageMissing() throws Exception {
        given(soapService.deleteSoapImage(1L, 99L))
                .willThrow(new com.handsoap.exception.ResourceNotFoundException(
                        "Image not found with id: 99 for soap id: 1"));

        mockMvc.perform(delete("/api/v1/admin/soaps/1/images/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("刪除單張圖片未登入時應回 401")
    void shouldReturn401WhenDeletingImageUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/soaps/1/images/7"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未登入時應回 401")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/soaps")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("非 ADMIN 角色時應回 403")
    @WithMockUser(roles = "USER")
    void shouldReturn403WhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/soaps")).andExpect(status().isForbidden());
    }

    private static PageImpl<SoapResponse> pageOf(SoapResponse... items) {
        return new PageImpl<>(List.of(items));
    }

    private static SoapResponse soap(Long id, String name) {
        return SoapResponse.builder().id(id).name(name).price(new BigDecimal("380")).isActive(true).build();
    }
}
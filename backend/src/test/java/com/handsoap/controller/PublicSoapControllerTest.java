package com.handsoap.controller;

import com.handsoap.config.SecurityConfig;
import com.handsoap.dto.SoapResponse;
import com.handsoap.service.SoapService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicSoapController.class)
@Import({SecurityConfig.class, ControllerTestSupport.class})
class PublicSoapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SoapService soapService;

    @Test
    @DisplayName("前台清單不需登入即可取得（統一查詢：無參數走全部）")
    void shouldReturnSoapsWithoutAuthentication() throws Exception {
        given(soapService.searchFiltered(eq(null), eq(null), any())).willReturn(pageOf(soap(1L, "薰衣草皂")));

        mockMvc.perform(get("/api/v1/soaps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("薰衣草皂"));
    }

    @Test
    @DisplayName("依 id 查詢應回傳該筆資料")
    void shouldReturnSoapById() throws Exception {
        given(soapService.getSoapById(1L)).willReturn(soap(1L, "薰衣草皂"));

        mockMvc.perform(get("/api/v1/soaps/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("薰衣草皂"));
    }

    @Test
    @DisplayName("依分類查詢應呼叫 getSoapsByCategory")
    void shouldReturnSoapsByCategory() throws Exception {
        given(soapService.searchFiltered(eq(7L), eq(null), any())).willReturn(pageOf(soap(2L, "分類皂")));

        mockMvc.perform(get("/api/v1/soaps/category/7"))
                .andExpect(status().isOk());

        verify(soapService).searchFiltered(eq(7L), eq(null), any());
    }

    @Test
    @DisplayName("搜尋端點缺少 keyword 應回 400（MissingServletRequestParameterException 已有專屬 handler）")
    void shouldReturn400WhenKeywordMissing() throws Exception {
        mockMvc.perform(get("/api/v1/soaps/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("keyword")));
    }

    @Test
    @DisplayName("搜尋端點應呼叫 searchSoaps")
    void shouldSearchSoaps() throws Exception {
        given(soapService.searchFiltered(eq(null), eq("cedar"), any())).willReturn(pageOf(soap(3L, "雪松皂")));

        mockMvc.perform(get("/api/v1/soaps/search").param("keyword", "cedar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("雪松皂"));

        verify(soapService).searchFiltered(eq(null), eq("cedar"), any());
    }

    @Test
    @DisplayName("統一查詢：categoryId+keyword 應走 searchFiltered 組合分支")
    void shouldSearchFilteredWithCategoryAndKeyword() throws Exception {
        given(soapService.searchFiltered(eq(7L), eq("玫瑰"), any())).willReturn(pageOf(soap(5L, "玫瑰皂")));

        mockMvc.perform(get("/api/v1/soaps").param("categoryId", "7").param("keyword", "玫瑰"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("玫瑰皂"));

        verify(soapService).searchFiltered(eq(7L), eq("玫瑰"), any());
    }

    @Test
    @DisplayName("統一查詢：僅 categoryId 應走分類分支")
    void shouldSearchFilteredWithCategoryOnly() throws Exception {
        given(soapService.searchFiltered(eq(7L), eq(null), any())).willReturn(pageOf(soap(2L, "分類皂")));

        mockMvc.perform(get("/api/v1/soaps").param("categoryId", "7"))
                .andExpect(status().isOk());

        verify(soapService).searchFiltered(eq(7L), eq(null), any());
    }

    @Test
    @DisplayName("統一查詢：僅 keyword 應走多欄位搜尋分支")
    void shouldSearchFilteredWithKeywordOnly() throws Exception {
        given(soapService.searchFiltered(eq(null), eq("玫瑰"), any())).willReturn(pageOf(soap(5L, "玫瑰皂")));

        mockMvc.perform(get("/api/v1/soaps").param("keyword", "玫瑰"))
                .andExpect(status().isOk());

        verify(soapService).searchFiltered(eq(null), eq("玫瑰"), any());
    }

    @Test
    @DisplayName("精選端點應呼叫 getFeaturedSoaps")
    void shouldReturnFeaturedSoaps() throws Exception {
        given(soapService.getFeaturedSoaps(any())).willReturn(pageOf(soap(4L, "精選皂")));

        mockMvc.perform(get("/api/v1/soaps/featured"))
                .andExpect(status().isOk());

        verify(soapService).getFeaturedSoaps(any());
    }

    private static PageImpl<SoapResponse> pageOf(SoapResponse... items) {
        return new PageImpl<>(List.of(items));
    }

    private static SoapResponse soap(Long id, String name) {
        return SoapResponse.builder().id(id).name(name).price(new BigDecimal("380")).isActive(true).build();
    }
}
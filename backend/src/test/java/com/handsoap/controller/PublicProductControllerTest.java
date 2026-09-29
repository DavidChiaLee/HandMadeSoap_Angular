package com.handsoap.controller;

import com.handsoap.config.SecurityConfig;
import com.handsoap.dto.EssentialOilResponse;
import com.handsoap.dto.OilResponse;
import com.handsoap.service.EssentialOilService;
import com.handsoap.service.OilService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PublicOilController.class, PublicEssentialOilController.class})
@Import({SecurityConfig.class, ControllerTestSupport.class})
class PublicProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OilService oilService;

    @MockBean
    private EssentialOilService essentialOilService;

    @Test
    @DisplayName("前台油品清單不需登入即可取得")
    void shouldReturnOilsWithoutAuthentication() throws Exception {
        given(oilService.getAllOils(any()))
                .willReturn(new PageImpl<>(List.of(OilResponse.builder().id(1L).name("橄欖油").build())));

        mockMvc.perform(get("/api/v1/oils"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("橄欖油"));
    }

    @Test
    @DisplayName("前台油品依 id 查詢應回傳該筆資料")
    void shouldReturnOilById() throws Exception {
        given(oilService.getOilById(1L)).willReturn(OilResponse.builder().id(1L).name("橄欖油").build());

        mockMvc.perform(get("/api/v1/oils/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("橄欖油"));
    }

    @Test
    @DisplayName("前台精油清單不需登入即可取得")
    void shouldReturnEssentialOilsWithoutAuthentication() throws Exception {
        given(essentialOilService.getAllEssentialOils(any()))
                .willReturn(new PageImpl<>(List.of(
                        EssentialOilResponse.builder().id(1L).name("薰衣草精油").build())));

        mockMvc.perform(get("/api/v1/essential-oils"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("薰衣草精油"));
    }

    @Test
    @DisplayName("前台精油依 id 查詢應回傳該筆資料")
    void shouldReturnEssentialOilById() throws Exception {
        given(essentialOilService.getEssentialOilById(1L))
                .willReturn(EssentialOilResponse.builder().id(1L).name("茶樹精油").build());

        mockMvc.perform(get("/api/v1/essential-oils/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("茶樹精油"));
    }
}
package com.handsoap.controller;

import com.handsoap.config.SecurityConfig;
import com.handsoap.repository.ContactRepository;
import com.handsoap.repository.EssentialOilRepository;
import com.handsoap.repository.OilRepository;
import com.handsoap.repository.SoapRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminStatsController.class)
@Import({SecurityConfig.class, ControllerTestSupport.class})
class AdminStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SoapRepository soapRepository;

    @MockBean
    private OilRepository oilRepository;

    @MockBean
    private EssentialOilRepository essentialOilRepository;

    @MockBean
    private ContactRepository contactRepository;

    @Test
    @DisplayName("應彙整各資料表的筆數")
    @WithMockUser(roles = "ADMIN")
    void shouldReturnAggregatedStats() throws Exception {
        given(soapRepository.count()).willReturn(12L);
        given(oilRepository.count()).willReturn(34L);
        given(essentialOilRepository.count()).willReturn(33L);
        given(contactRepository.count()).willReturn(5L);

        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.soaps").value(12))
                .andExpect(jsonPath("$.data.oils").value(34))
                .andExpect(jsonPath("$.data.essentialOils").value(33))
                .andExpect(jsonPath("$.data.contacts").value(5));
    }

    @Test
    @DisplayName("未登入時應回 401")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("非 ADMIN 角色時應回 403")
    @WithMockUser(roles = "USER")
    void shouldReturn403WhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats")).andExpect(status().isForbidden());
    }
}
package com.handsoap.controller;

import com.handsoap.config.ClientIpResolver;
import com.handsoap.config.SecurityConfig;
import com.handsoap.dto.CategoryResponse;
import com.handsoap.dto.ContactRequest;
import com.handsoap.entity.Contact;
import com.handsoap.service.CategoryService;
import com.handsoap.service.ContactService;
import com.handsoap.service.ContactSubmissionThrottleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PublicCategoryController.class, PublicContactController.class})
@Import({SecurityConfig.class, ControllerTestSupport.class, ClientIpResolver.class})
class PublicCategoryContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private ContactService contactService;

    /**
     * 每 IP 滑動窗口限流（review 728）。
     *
     * <p>同 AuthControllerTest 對 LoginAttemptService 的理由：本測試關注
     * Controller 的「放行 / 擋下 / 記錄」協調行為，滑動窗口本身由
     * {@code ContactSubmissionThrottleServiceTest} 獨立驗證。</p>
     */
    @MockBean
    private ContactSubmissionThrottleService contactThrottle;

    @BeforeEach
    void allowByDefault() {
        // 每個測試預設「未被限流」；個別測試需要時再覆寫成指定的剩餘時間。
        lenient().when(contactThrottle.check(anyString())).thenReturn(Duration.ZERO);
    }

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
        // 成功寫入後才佔額度（MockMvc 未設定時 remoteAddr 為 127.0.0.1）
        verify(contactThrottle).recordSuccess("127.0.0.1");
    }

    /**
     * Honeypot 非空 = bot（review 728）。
     *
     * <p>關鍵在「silent success」：回 201 讓 bot 以為成功、不會改策略再試；
     * 同時絕不可寫庫。若這裡改回 400，等於告訴 bot 蜜罐被識破。</p>
     */
    @Test
    @DisplayName("honeypot 欄位非空時應 silent success 且不得寫入資料庫（review 728）")
    void shouldSilentlyDropContactWithHoneypotFilled() throws Exception {
        mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bot\",\"email\":\"bot@spam.com\",\"subject\":\"買藥\","
                                + "\"message\":\"spam\",\"company\":\"SpamCo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(contactService, never()).saveContact(any(ContactRequest.class));
        verify(contactThrottle, never()).recordSuccess(anyString());
    }

    /** 超過每 IP 額度時應 429 + Retry-After，且不得再寫庫。 */
    @Test
    @DisplayName("超過每 IP 送出上限時應回 429 並附 Retry-After（review 728）")
    void shouldReturn429WhenContactThrottled() throws Exception {
        given(contactThrottle.check(anyString())).willReturn(Duration.ofSeconds(30));

        mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"王小明\",\"email\":\"a@b.com\",\"subject\":\"問題\",\"message\":\"內容\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.success").value(false));

        verify(contactService, never()).saveContact(any(ContactRequest.class));
        verify(contactThrottle, never()).recordSuccess(anyString());
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
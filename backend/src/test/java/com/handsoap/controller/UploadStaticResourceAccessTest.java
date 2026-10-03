package com.handsoap.controller;

import com.handsoap.config.SecurityConfig;
import com.handsoap.service.SoapService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 守護 {@code /uploads/**} 的匿名存取。
 *
 * <p>前端以 {@code <img src="/uploads/...">} 載入圖片，瀏覽器不會附帶 JWT，
 * 因此這條路徑必須 permitAll。review H3 已把 {@code SecurityConfig} 的
 * {@code anyRequest()} 改為 {@code authenticated()}，若漏掉這個 matcher，
 * 正式站會「所有圖片變 401」，而且前端只會顯示 placeholder、不會報錯，極難察覺。</p>
 *
 * <p>本測試在 H3 修正後才真正具有鑑別力：{@code authenticated()} 生效的話，
 * 少一條 permitAll matcher，這兩個測試就會立刻紅燈。</p>
 */
@WebMvcTest(PublicSoapController.class)
@Import({SecurityConfig.class, ControllerTestSupport.class})
class UploadStaticResourceAccessTest {

    @Autowired
    private MockMvc mockMvc;

    /** 本測試只驗證授權規則，controller 僅作為載入 Spring Security 設定的載體。 */
    @MockBean
    private SoapService soapService;

    /**
     * 授權守護：匿名請求 {@code /uploads/**} 不得回 401。
     *
     * <p>刻意不斷言 404：檔案不存在時 Spring 會拋 NoResourceFoundException，
     * 而本專案 {@code GlobalExceptionHandler.handleGenericException} 會把它
     * 一律轉成 500，實際狀態碼取決於全域錯誤處理而非授權邏輯。
     * 真正要防的是「因未放行而被擋成 401」，故只檢查狀態碼不是 401。</p>
     */
    private void assertNotUnauthorized(String uri) throws Exception {
        int status = mockMvc.perform(get(uri))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status)
                .as("匿名請求 %s 不應因授權被擋成 401（前端 <img> 不帶 JWT）", uri)
                .isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("匿名請求 /uploads/** 不應被擋下（不得為 401 未授權）")
    void shouldNotRequireAuthenticationForUploads() throws Exception {
        assertNotUnauthorized("/uploads/any-image.png");
    }

    @Test
    @DisplayName("匿名請求 /uploads/ 底下的多層路徑也不應被擋下")
    void shouldNotRequireAuthenticationForNestedUploadsPath() throws Exception {
        assertNotUnauthorized("/uploads/2026/09/any-image.png");
    }
}
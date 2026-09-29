package com.handsoap.controller;

import com.handsoap.config.FileUploadConfig;
import com.handsoap.config.JwtAuthFilter;
import com.handsoap.config.JwtUtil;
import com.handsoap.repository.AdminRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 提供各 Controller 測試共用的缺漏 bean。
 *
 * <p>兩個重點：</p>
 * <ul>
 *   <li>{@code @WebMvcTest} 會納入 WebMvcConfigurer（WebConfig），但其依賴的
 *       FileUploadConfig 是一般 @Configuration，不在切片掃描範圍，需手動補上。</li>
 *   <li>JwtAuthFilter 不可用 @MockBean 取代，否則 mock filter 不會呼叫 filterChain，
 *       會吞掉所有請求導致一律回 200 空內容；此處改提供真實 filter，
 *       其依賴的 JwtUtil / AdminRepository 則以 Mockito mock 注入。</li>
 * </ul>
 */
@TestConfiguration
public class ControllerTestSupport {

    @Bean
    public FileUploadConfig fileUploadConfig() {
        FileUploadConfig config = new FileUploadConfig();
        ReflectionTestUtils.setField(config, "uploadDir", "build/test-uploads");
        return config;
    }

    @Bean
    public JwtUtil jwtUtil() {
        return Mockito.mock(JwtUtil.class);
    }

    @Bean
    public AdminRepository adminRepository() {
        return Mockito.mock(AdminRepository.class);
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil, AdminRepository adminRepository) {
        return new JwtAuthFilter(jwtUtil, adminRepository);
    }
}
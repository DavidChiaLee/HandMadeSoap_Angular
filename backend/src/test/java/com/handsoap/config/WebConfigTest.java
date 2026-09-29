package com.handsoap.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebConfigTest {

    private FileUploadConfig fileUploadConfig;
    private WebConfig webConfig;

    @BeforeEach
    void setUp() {
        fileUploadConfig = mock(FileUploadConfig.class);
        webConfig = new WebConfig(fileUploadConfig);
    }

    @Test
    @DisplayName("設定上傳目錄時應註冊 /uploads/** 靜態資源")
    void shouldRegisterUploadResourceHandler() {
        when(fileUploadConfig.getUploadDir()).thenReturn("/app/uploads");
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
        ResourceHandlerRegistration registration = mock(ResourceHandlerRegistration.class);
        when(registry.addResourceHandler(anyString())).thenReturn(registration);
        when(registration.addResourceLocations(anyString())).thenReturn(registration);

        webConfig.addResourceHandlers(registry);

        verify(registry).addResourceHandler("/uploads/**");
        verify(registration).addResourceLocations("file:/app/uploads/");
    }

    @Test
    @DisplayName("上傳目錄為空字串時不應註冊靜態資源")
    void shouldNotRegisterWhenUploadDirEmpty() {
        when(fileUploadConfig.getUploadDir()).thenReturn("");
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);

        webConfig.addResourceHandlers(registry);

        verify(registry, never()).addResourceHandler(anyString());
    }

    @Test
    @DisplayName("上傳目錄為 null 時不應註冊靜態資源")
    void shouldNotRegisterWhenUploadDirNull() {
        when(fileUploadConfig.getUploadDir()).thenReturn(null);
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);

        webConfig.addResourceHandlers(registry);

        verify(registry, never()).addResourceHandler(anyString());
    }
}
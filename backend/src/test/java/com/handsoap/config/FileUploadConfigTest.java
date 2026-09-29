package com.handsoap.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class FileUploadConfigTest {

    @Test
    @DisplayName("應回傳注入的上傳目錄")
    void shouldReturnConfiguredUploadDir() {
        FileUploadConfig config = new FileUploadConfig();
        ReflectionTestUtils.setField(config, "uploadDir", "/app/uploads");

        assertThat(config.getUploadDir()).isEqualTo("/app/uploads");
    }

    @Test
    @DisplayName("Windows 路徑應原樣回傳")
    void shouldReturnWindowsPathUnchanged() {
        FileUploadConfig config = new FileUploadConfig();
        ReflectionTestUtils.setField(config, "uploadDir", "C:\\handsoap\\uploads");

        assertThat(config.getUploadDir()).isEqualTo("C:\\handsoap\\uploads");
    }
}
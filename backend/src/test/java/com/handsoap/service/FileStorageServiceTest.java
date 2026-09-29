package com.handsoap.service;

import com.handsoap.config.FileUploadConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private FileUploadConfig fileUploadConfig;

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService(fileUploadConfig);
    }

    @Test
    @DisplayName("存檔後應產生檔案，且檔名為 UUID 加原副檔名")
    void shouldStoreFileWithUuidName() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        MultipartFile file = new MockMultipartFile(
                "file", "myphoto.jpg", "image/jpeg", "image-bytes".getBytes());

        String filename = service.saveFile(file);

        assertThat(filename).matches("[0-9a-f-]{36}\\.jpg");
        assertThat(tempDir.resolve(filename)).exists();
    }

    @Test
    @DisplayName("原檔名沒有副檔名時應只產生 UUID")
    void shouldStoreFileWithoutExtension() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        MultipartFile file = new MockMultipartFile("file", "noextension", "application/octet-stream", "x".getBytes());

        String filename = service.saveFile(file);

        assertThat(filename).matches("[0-9a-f-]{36}");
        assertThat(tempDir.resolve(filename)).exists();
    }

    @Test
    @DisplayName("檔名含多個點時應取最後一個副檔名")
    void shouldUseLastExtension() {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        MultipartFile file = new MockMultipartFile("file", "a.b.c.png", "image/png", "x".getBytes());

        assertThat(service.saveFile(file)).endsWith(".png");
    }

    @Test
    @DisplayName("儲存的內容應與上傳內容一致")
    void shouldPreserveFileContent() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        byte[] content = "hello-soap".getBytes();
        MultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", content);

        String filename = service.saveFile(file);

        assertThat(Files.readAllBytes(tempDir.resolve(filename))).isEqualTo(content);
    }

    @Test
    @DisplayName("上傳兩次不應互相覆蓋（檔名唯一）")
    void shouldGenerateUniqueNames() {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "x".getBytes());

        assertThat(service.saveFile(file)).isNotEqualTo(service.saveFile(file));
    }

    @Test
    @DisplayName("刪除既有檔案後該檔案應不存在")
    void shouldDeleteExistingFile() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "x".getBytes());
        String filename = service.saveFile(file);

        service.deleteFile(filename);

        assertThat(tempDir.resolve(filename)).doesNotExist();
    }

    @Test
    @DisplayName("刪除不存在的檔案不應拋出例外")
    void shouldIgnoreMissingFileOnDelete() {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());

        service.deleteFile("not-exists.png");
    }

    @Test
    @DisplayName("讀取檔案失敗時應包成 RuntimeException")
    void shouldWrapIoException() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        given(file.getOriginalFilename()).willReturn("broken.png");
        org.mockito.Mockito.doThrow(new IOException("disk error"))
                .when(file).getInputStream();

        assertThatThrownBy(() -> service.saveFile(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to store file");
    }
}
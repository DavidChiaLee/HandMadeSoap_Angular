package com.handsoap.service;

import com.handsoap.config.FileUploadConfig;
import com.handsoap.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * {@link FileStorageService} 測試。
 *
 * <p>分兩組：正向案例（檔名乾淨、副檔名在白名單內）與負向／邊界案例
 * （白名單外的副檔名、無效檔名、路徑分隔符、{@code deleteFile} 的穿越嘗試），
 * 後者都會額外斷言磁碟上沒有留下任何檔案。
 *
 * <p>注意：{@code saveFile} 的白名單驗證使路徑穿越無法發生，因此
 * {@code normalize() + startsWith()} 這層圍堵在 {@code saveFile} 上是<b>測不出來</b>的。
 * 真正需要被驗證的是 {@code deleteFile}（其參數不經過白名單），故下方刻意保留兩個
 * 針對 {@code deleteFile} 的穿越案例，避免該層防護被誤認為「有測試覆蓋」而日後被移除。
 */
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

    /** 斷言上傳目錄下沒有任何一般檔案產生（用來證明驗證發生在寫檔之前）。 */
    private void assertNothingStored() throws IOException {
        try (Stream<Path> files = Files.walk(tempDir)) {
            assertThat(files.filter(Files::isRegularFile).count())
                    .as("被拒絕的上傳不應在磁碟留下任何檔案")
                    .isZero();
        }
    }

    /**
     * 產生帶有合法檔頭簽章、但不需要是真實圖片資料的位元組。
     *
     * <p>{@code FileStorageService} 只比對檔頭 12 bytes，因此測試不需要內嵌
     * 真正的 PNG/JPEG 檔案；這也是實作選用 magic bytes 而非 {@code ImageIO.read()}
     * 的原因之一（後者會完整解碼，測試成本高且有 decompression bomb 風險）。
     */
    private static byte[] imageBytesFor(String extension) {
        return switch (extension) {
            case "png" -> new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
            case "jpg", "jpeg" -> new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            case "gif" -> "GIF89a-payload".getBytes(StandardCharsets.US_ASCII);
            // WebP 是 RIFF 容器：檔頭 "RIFF"、offset 8–11 才是 "WEBP"
            case "webp" -> "RIFF____WEBP".getBytes(StandardCharsets.US_ASCII);
            default -> throw new IllegalArgumentException("未定義的副檔名: " + extension);
        };
    }

    /** 依副檔名自動配上合法簽章內容的 MockMultipartFile。 */
    private static MultipartFile imageFile(String originalFilename, String extension) {
        return new MockMultipartFile("file", originalFilename, contentTypeFor(extension), imageBytesFor(extension));
    }

    private static String contentTypeFor(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    @Test
    @DisplayName("存檔後應產生檔案，且檔名為 UUID 加原副檔名")
    void shouldStoreFileWithUuidName() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        MultipartFile file = imageFile("myphoto.jpg", "jpg");

        String filename = service.saveFile(file);

        assertThat(filename).matches("[0-9a-f-]{36}\\.jpg");
        assertThat(tempDir.resolve(filename)).exists();
    }

    @Test
    @DisplayName("檔名含多個點時應取最後一個副檔名")
    void shouldUseLastExtension() {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        MultipartFile file = imageFile("a.b.c.png", "png");

        assertThat(service.saveFile(file)).endsWith(".png");
    }

    @Test
    @DisplayName("儲存的內容應與上傳內容一致")
    void shouldPreserveFileContent() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        byte[] content = imageBytesFor("png");
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", content);

        String filename = service.saveFile(file);

        assertThat(Files.readAllBytes(tempDir.resolve(filename))).isEqualTo(content);
    }

    @Test
    @DisplayName("上傳兩次不應互相覆蓋（檔名唯一）")
    void shouldGenerateUniqueNames() {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        MultipartFile file = imageFile("a.png", "png");

        assertThat(service.saveFile(file)).isNotEqualTo(service.saveFile(file));
    }

    @Test
    @DisplayName("刪除既有檔案後該檔案應不存在")
    void shouldDeleteExistingFile() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        MultipartFile file = imageFile("a.png", "png");
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
    @DisplayName("檔名為 null 或空白時刪除應直接略過而非拋出 NPE")
    void shouldIgnoreBlankNameOnDelete() {
        // 刻意不 stub getUploadDir()：空白檔名應在任何檔案系統操作之前就被擋掉
        service.deleteFile(null);
        service.deleteFile("   ");
    }

    @Test
    @DisplayName("讀取檔案失敗時應包成 RuntimeException")
    void shouldWrapIoException() throws IOException {
        // 刻意不 stub getUploadDir()：讀取檔頭就失敗，不會走到解析上傳目錄那一步
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        given(file.getOriginalFilename()).willReturn("broken.png");
        org.mockito.Mockito.doThrow(new IOException("disk error"))
                .when(file).getInputStream();

        assertThatThrownBy(() -> service.saveFile(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to store file");
    }

    // ---------- 負向／邊界案例 ----------

    @ParameterizedTest
    @CsvSource({
            "photo.JPG, jpg",
            "photo.JPEG, jpeg",
            "PHOTO.PNG, png",
            "shot.WEBP, webp",
            "shot.GIF, gif"
    })
    @DisplayName("副檔名大小寫不同仍應接受，並一律正規化為小寫")
    void shouldAcceptUppercaseExtension(String originalFilename, String expectedExtension) {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        MultipartFile file = imageFile(originalFilename, expectedExtension);

        assertThat(service.saveFile(file)).endsWith("." + expectedExtension);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a.svg",    // 可內嵌 script → 儲存型 XSS
            "a.html",   // 同上
            "a.htm",
            "a.php",
            "a.jsp",
            "a.txt",    // 非圖片
            "a.exe"
    })
    @DisplayName("非白名單的副檔名應被拒絕（證明是集合白名單而非僅字元集檢查）")
    void shouldRejectNonWhitelistedExtension(String originalFilename) throws IOException {
        // 刻意不 stub getUploadDir()：被拒絕的檔案不該走到解析上傳目錄那一步
        MultipartFile file = new MockMultipartFile("file", originalFilename, "image/png", "x".getBytes());

        assertThatThrownBy(() -> service.saveFile(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("僅支援");
        assertNothingStored();
    }

    @ParameterizedTest
    @ValueSource(strings = {"noextension", "a.", ""})
    @DisplayName("檔名缺少有效副檔名時應被拒絕（不得靜默產生無副檔名檔案）")
    void shouldRejectFilenameWithoutValidExtension(String originalFilename) throws IOException {
        // 刻意不 stub getUploadDir()：被拒絕的檔案不該走到解析上傳目錄那一步
        MultipartFile file = new MockMultipartFile("file", originalFilename, "image/png", "x".getBytes());

        assertThatThrownBy(() -> service.saveFile(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("檔名不合法");
        assertNothingStored();
    }

    @Test
    @DisplayName("原始檔名為 null 時應被拒絕")
    void shouldRejectNullOriginalFilename() throws IOException {
        // 刻意不 stub getUploadDir()：被拒絕的檔案不該走到解析上傳目錄那一步
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        given(file.getOriginalFilename()).willReturn(null);

        assertThatThrownBy(() -> service.saveFile(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("檔名不合法");
        assertNothingStored();
    }

    @Test
    @DisplayName("上傳 0 bytes 空內容時應被拒絕")
    void shouldRejectEmptyContent() throws IOException {
        // 刻意不 stub getUploadDir()：被拒絕的檔案不該走到解析上傳目錄那一步
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.saveFile(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("檔案內容為空");
        assertNothingStored();
    }

    /**
     * 副檔名含路徑分隔符時<b>不會被拒絕</b>，而是被「中和」成安全的平面檔名：
     * {@code safeExtension} 只取最後一個點之後的 {@code "png"} 並比對白名單，
     * 檔名中的 {@code /}、{@code \}、{@code ..} 全部被丟棄，因此不可能形成穿越。
     * 這裡同時驗證兩件事：產出的檔名是乾淨的 {@code <uuid>.png}，且上傳目錄之外沒有檔案。
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "a.png/../../escaped.png",
            "a.png\\..\\..\\escaped.png"
    })
    @DisplayName("檔名含路徑分隔符（/ 與 \\）時應被中和為平面檔名，不得寫入上傳目錄之外")
    void shouldNeutralizePathSeparatorsInFilename(String originalFilename) throws IOException {
        Path uploadDir = Files.createDirectories(tempDir.resolve("uploads"));
        Path escapeTarget = tempDir.resolve("escaped.png");
        given(fileUploadConfig.getUploadDir()).willReturn(uploadDir.toString());
        MultipartFile file = imageFile(originalFilename, "png");

        String filename = service.saveFile(file);

        assertThat(filename).matches("[0-9a-f-]{36}\\.png");
        assertThat(uploadDir.resolve(filename)).exists();
        assertThat(escapeTarget)
                .as("穿越目標不得被寫入")
                .doesNotExist();
    }

    /**
     * 這是唯一能讓 {@code normalize() + startsWith()} 這層圍堵產生鑑別力的案例。
     *
     * <p>{@code saveFile} 有白名單保護，穿越參數進不了路徑；但 {@code deleteFile} 的參數
     * 直接來自呼叫端（實務上會是資料庫的 imagePath），而 POSIX 核心逐段解析 {@code ..}
     * 且不需要中間目錄存在，因此這是可直接觸發的任意檔案刪除。
     */
    @Test
    @DisplayName("刪除時若檔名嘗試跳出上傳目錄應拒絕，且目標檔案必須仍在")
    void shouldRejectTraversalOnDelete() throws IOException {
        Path uploadDir = Files.createDirectories(tempDir.resolve("uploads"));
        Path outsideFile = tempDir.resolve("outside.png");
        Files.writeString(outsideFile, "must-stay");
        given(fileUploadConfig.getUploadDir()).willReturn(uploadDir.toString());

        assertThatThrownBy(() -> service.deleteFile("../outside.png"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("檔案路徑不合法");
        assertThat(outsideFile)
                .as("上傳目錄外的檔案不得被刪除")
                .exists();
    }

    @Test
    @DisplayName("刪除時若檔名為絕對路徑應拒絕（resolve 遇絕對路徑會直接採用該路徑）")
    void shouldRejectAbsolutePathOnDelete() throws IOException {
        Path uploadDir = Files.createDirectories(tempDir.resolve("uploads"));
        Path outsideFile = tempDir.resolve("absolute-target.png");
        Files.writeString(outsideFile, "must-stay");
        given(fileUploadConfig.getUploadDir()).willReturn(uploadDir.toString());

        assertThatThrownBy(() -> service.deleteFile(outsideFile.toAbsolutePath().toString()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("檔案路徑不合法");
        assertThat(outsideFile).exists();
    }

    // ---------- H2：檔案內容（magic bytes）驗證 ----------

    /**
     * 副檔名合法但內容不是該格式 → 必須拒絕。
     *
     * <p>這些案例在加入 magic bytes 之前是<b>會被存檔的</b>：副檔名白名單只看檔名，
     * 因此 {@code evil.png} 內容是 HTML 也照樣進得去。屬於回歸防護。
     */
    @ParameterizedTest
    @CsvSource({
            "evil.png,  <script>alert(1)</script>",
            "evil.jpg,  <html><body>hi</body></html>",
            "evil.jpeg, PK zip-renamed",
            "evil.gif,  just-plain-text",
            "evil.webp, RIFF____WAVE"
    })
    @DisplayName("副檔名合法但內容不是該圖片格式時應拒絕（偽裝檔案）")
    void shouldRejectContentNotMatchingExtension(String originalFilename, String content)
            throws IOException {
        // 刻意不 stub getUploadDir()：被拒絕的檔案不該走到解析上傳目錄那一步
        MultipartFile file = new MockMultipartFile(
                "file", originalFilename, "application/octet-stream",
                content.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.saveFile(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("檔案內容與副檔名不符");
        assertNothingStored();
    }

    @Test
    @DisplayName("WebP 必須同時滿足 RIFF 檔頭與 offset 8 的 WEBP 標記")
    void shouldAcceptWebPWithRiffAndWebpMarker() throws IOException {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        MultipartFile file = imageFile("photo.webp", "webp");

        String filename = service.saveFile(file);

        assertThat(filename).endsWith(".webp");
        assertThat(tempDir.resolve(filename)).exists();
    }

    @Test
    @DisplayName("GIF87a 舊格式簽章也應被接受")
    void shouldAcceptLegacyGifSignature() {
        given(fileUploadConfig.getUploadDir()).willReturn(tempDir.toString());
        MultipartFile file = new MockMultipartFile(
                "file", "old.gif", "image/gif",
                "GIF87a-payload".getBytes(StandardCharsets.US_ASCII));

        assertThat(service.saveFile(file)).endsWith(".gif");
    }

    @Test
    @DisplayName("檔案長度不足以容納簽章時應拒絕而非拋出例外")
    void shouldRejectContentShorterThanSignature() throws IOException {
        // 刻意不 stub getUploadDir()：被拒絕的檔案不該走到解析上傳目錄那一步
        MultipartFile file = new MockMultipartFile("file", "tiny.png", "image/png", "x".getBytes());

        assertThatThrownBy(() -> service.saveFile(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("檔案內容與副檔名不符");
        assertNothingStored();
    }

    // ---------- P3：白名單擴充的回歸防護 ----------

    /**
     * 釘住白名單的組成判準。
     *
     * <p><b>新增成員前必讀：</b>該型別必須不能被瀏覽器當作 document 直接開啟並執行腳本。
     * 也就是<b>不可</b>加入 {@code svg}（{@code image/svg+xml} 可內嵌 script）、
     * {@code html}/{@code xhtml}/{@code xml}。這正是本測試把這些格式列為拒絕的原因：
     * 若有人日後「順手」把 svg 加進 {@code ALLOWED_EXTENSIONS}，此測試就會失敗。
     *
     * <p>採行為斷言而非直接檢視 {@code ALLOWED_EXTENSIONS} 常數，
     * 以免為了測試而犧牲封裝。
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "a.svg",     // image/svg+xml 可內嵌 <script> → 儲存型 XSS
            "a.xml",     // 可帶 XSLT / 處理指令
            "a.xhtml",
            "a.svgz",    // gzip 版的 svg，同樣可內嵌 script
            "a.avif",    // 尚未納入白名單；加入前需先確認支援度
            "a.bmp",
            "a.tif",     // 含 EXIF，允許前需評估中繼資料外洩
    })
    @DisplayName("白名單不得收錄會被瀏覽器當作 document 執行的型別")
    void shouldKeepExecutableFormatsOutOfWhitelist(String originalFilename) {
        MultipartFile file = new MockMultipartFile(
                "file", originalFilename, "application/octet-stream",
                imageBytesFor("png"));

        assertThatThrownBy(() -> service.saveFile(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("僅支援");
    }
}
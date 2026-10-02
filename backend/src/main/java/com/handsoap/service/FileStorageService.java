package com.handsoap.service;

import com.handsoap.config.FileUploadConfig;
import com.handsoap.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 檔案儲存服務。
 *
 * <p>安全設計（review H1）：
 * <ol>
 *   <li><b>第一層 — 不可信輸入不進檔名。</b>副檔名一律以白名單比對，
 *       {@link #safeExtension(MultipartFile)} 只回傳 {@code ALLOWED_EXTENSIONS} 中的值。
 *       因為只取「最後一個點之後」的字串再比對，檔名中的 {@code /}、{@code \}、{@code .}
 *       永遠不會進入結果，單靠這一層就使路徑穿越不可能發生。</li>
 *   <li><b>第二層 — 圍堵檢查（防禦深度）。</b>{@link #resolveInsideUploadDir} 以
 *       {@code normalize()} + {@code startsWith()} 確認最終路徑仍在上傳目錄內。
 *       對 {@link #saveFile} 而言這是無法觸發的保險；
 *       真正需要它的是 {@link #deleteFile} — 該方法的參數並非由白名單產生
 *       （未來會是資料庫的 imagePath），若為 {@code ../../etc/passwd} 這類值，
 *       POSIX 核心會逐段解析 {@code ..} 且不需要中間目錄存在，將直接命中上傳目錄之外。
 *       兩條路徑共用同一個 helper 正是為了不讓這個差異被漏掉。</li>
 * </ol>
 */
@Service
public class FileStorageService {

    /**
     * 允許的副檔名白名單（不含前導點、全部小寫）。
     * 對齊前端三處 {@code accept="image/*"}；亦擋下 svg / html 等造成儲存型 XSS 的型別。
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private static final String DEFAULT_UPLOAD_DIR = "/app/uploads";

    /** 讀取檔頭所需的位元組數：最長的簽章是 PNG 的 8 bytes 加上 WebP 的第二段（offset 8–11）。 */
    private static final int SIGNATURE_LENGTH = 12;

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF87A_SIGNATURE = ascii("GIF87a");
    private static final byte[] GIF89A_SIGNATURE = ascii("GIF89a");
    private static final byte[] RIFF_SIGNATURE = ascii("RIFF");
    private static final byte[] WEBP_SIGNATURE = ascii("WEBP");

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private final FileUploadConfig fileUploadConfig;

    public FileStorageService(FileUploadConfig fileUploadConfig) {
        this.fileUploadConfig = fileUploadConfig;
    }

    public String saveFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("檔案內容為空，請重新選擇");
        }

        // 驗證刻意放在 try 之外：不可丟 IOException 的包裝路徑，
        // 否則會落到全域 handler 回 500 並把絕對路徑回給前端。
        String extension = safeExtension(file);
        verifyMagicBytes(file, extension);
        warnIfContentTypeMismatched(file, extension);

        String newFilename = UUID.randomUUID() + "." + extension;
        Path uploadPath = uploadRoot();
        Path filePath = resolveInsideUploadDir(uploadPath, newFilename);

        try {
            Files.createDirectories(uploadPath);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, filePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }

        return newFilename;
    }

    public void deleteFile(String filename) {
        if (filename == null || filename.isBlank()) {
            // 沒有檔名代表沒有東西可刪；直接略過，避免 NPE 被轉成 500
            return;
        }

        Path filePath = resolveInsideUploadDir(uploadRoot(), filename);

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + e.getMessage(), e);
        }
    }

    /**
     * 解析上傳根目錄（絕對、已正規化）。兩邊都正規化才能讓 {@code startsWith} 比較正確。
     */
    private Path uploadRoot() {
        String uploadDir = fileUploadConfig.getUploadDir();
        if (uploadDir == null || uploadDir.isBlank()) {
            uploadDir = DEFAULT_UPLOAD_DIR;
        }
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * 把檔名解析到上傳目錄內，若結果逃逸則拒絕。
     *
     * <p>{@link Path#startsWith(Path)} 是「元素級」前綴比對（不是 {@code String.startsWith}），
     * 因此 {@code /uploads2/x} 不會被誤判為位於 {@code /uploads} 之下；
     * Windows 上則為大小寫不敏感比較。
     * 另注意 {@code resolve} 遇到絕對路徑會直接回傳該絕對路徑，同樣會在此被擋下。
     */
    private Path resolveInsideUploadDir(Path uploadPath, String name) {
        Path resolved = uploadPath.resolve(name).normalize();
        if (!resolved.startsWith(uploadPath)) {
            throw new BadRequestException("檔案路徑不合法，請重新上傳");
        }
        return resolved;
    }

    /**
     * 取出白名單內的副檔名（小寫、不含前導點），否則拒絕。
     *
     * <p>大小寫以 {@link Locale#ROOT} 正規化：iOS / Windows 常見 {@code .JPG}、{@code .JPEG}，
     * 而土耳其語 locale 下 {@code toLowerCase()} 會把 {@code I} 轉成 {@code İ} 導致比對失敗。
     */
    private String safeExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BadRequestException("檔名不合法，請重新上傳");
        }

        int dotIndex = originalFilename.lastIndexOf('.');
        // dotIndex == length-1 代表檔名結尾只有一個點，沒有實際副檔名
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            throw new BadRequestException("檔名不合法，請重新上傳");
        }

        String extension = originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("僅支援 " + String.join("、", ALLOWED_EXTENSIONS) + " 格式的圖片");
        }
        return extension;
    }

    /**
     * 以檔頭簽章確認內容確實是該副檔名對應的圖片格式（review H2）。
     *
     * <p><b>定位說明（避免日後被高估）：這是資料衛生，不是 XSS 的主要防線。</b>
     * 真正擋住儲存型 XSS 的是前兩道：副檔名白名單（{@link #safeExtension}）
     * 加上 nginx／Spring 的 {@code nosniff} 與 CSP。此檢查的實際價值在於
     * 擋下「zip 改名 .png」這類會在 uploads 目錄無限堆積的非圖片檔案。
     *
     * <p><b>為什麼不用 {@code ImageIO.read()}：</b>JDK 的 {@code javax.imageio.ImageIO}
     * 沒有內建 WebP reader（實測 {@code getImageReadersByFormatName("webp")} 回 false，
     * png/jpeg/gif 為 true），採用它會誤殺所有合法 WebP 上傳；且它會完整解碼，
     * 20MB 上限的惡意 PNG 可解壓成數 GB 造成 OOM（decompression bomb）。
     * magic bytes 只需讀檔頭 12 bytes，零依賴、零 DoS 風險、且不影響 WebP。
     */
    private void verifyMagicBytes(MultipartFile file, String extension) {
        byte[] head = readSignature(file);

        boolean matches = switch (extension) {
            case "jpg", "jpeg" -> startsWith(head, JPEG_SIGNATURE);
            case "png" -> startsWith(head, PNG_SIGNATURE);
            case "gif" -> startsWith(head, GIF87A_SIGNATURE) || startsWith(head, GIF89A_SIGNATURE);
            // WebP 是 RIFF 容器，格式標記在 offset 8–11 而非檔頭
            case "webp" -> startsWith(head, RIFF_SIGNATURE)
                    && head.length >= 12
                    && Arrays.equals(Arrays.copyOfRange(head, 8, 12), WEBP_SIGNATURE);
            default -> false;
        };

        if (!matches) {
            throw new BadRequestException("檔案內容與副檔名不符，請重新上傳");
        }
    }

    /** 讀取檔頭 {@link #SIGNATURE_LENGTH} bytes；讀取失敗沿用既有的 IOException 包裝。 */
    private byte[] readSignature(MultipartFile file) {
        byte[] buffer = new byte[SIGNATURE_LENGTH];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(buffer, 0, SIGNATURE_LENGTH);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
        return Arrays.copyOf(buffer, read);
    }

    private boolean startsWith(byte[] head, byte[] signature) {
        if (head.length < signature.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOfRange(head, 0, signature.length), signature);
    }

    /**
     * 僅記錄 Content-Type 與副檔名不一致的情形，<b>不作為拒絕條件</b>。
     *
     * <p>{@code getContentType()} 完全由客戶端控制（與 {@code originalFilename} 同一類輸入），
     * 拿來當安全判斷沒有意义；部分瀏覽器對 {@code .jpg} 也會送
     * {@code application/octet-stream}，硬性比對會誤殺合法上傳。
     * 這裡只留下稽核用的線索。
     */
    private void warnIfContentTypeMismatched(MultipartFile file, String extension) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (!normalized.contains("/" + extension)
                && !normalized.contains("/x-" + extension)
                && !(extension.equals("jpg") && normalized.contains("/jpeg"))) {
            log.warn("上傳檔案的 Content-Type 與副檔名不符：extension={} contentType={}",
                    extension, contentType);
        }
    }
}

package com.handsoap.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 為 {@code /uploads/**} 的回應補上安全標頭（review H2）。
 *
 * <p>{@code nginx.conf} 的 {@code location ^~ /uploads/} 已經送這些標頭，但防護不應
 * 依賴單一入口。以下路徑都會繞過 nginx、當時是零防護：
 * <ul>
 *   <li>本機開發：{@code environment.ts} 的 {@code uploadUrl} 指向
 *       {@code http://localhost:8080/uploads/}，{@code ng serve} 直接打後端。</li>
 *   <li>日後若把 {@code docker-compose.yml} 的 {@code ports} 加回來（review H8 指出的問題）、
 *       或改用 k8s Service / Ingress 直通後端。</li>
 * </ul>
 *
 * <p>刻意<b>不</b>放進 {@code SecurityConfig.headers()}：那會套用到所有回應
 * （包含 API 的 JSON），而 CSP 對 JSON 沒意義，還可能干擾既有回應。
 *
 * <p>值與 nginx 保持一致；注意經 nginx 代理時會同時出現兩個 CSP 標頭
 * （{@code add_header} 是附加不是覆寫），瀏覽器取多份 CSP 的交集、兩者同值故無害。
 */
@Component
public class UploadResourceHeaderFilter extends OncePerRequestFilter {

    private static final String UPLOAD_PATH_PREFIX = "/uploads/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(UPLOAD_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 上傳內容是不可信的（review H1/H2）。副檔名已限白名單，但預防性的
        // nosniff 仍必須有：擋下 Content-Type 嗅探（例如偽裝成 .png 的 HTML）。
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");

        // 若惡意內容被「直接開啟」成為 document，default-src 'none' 擋掉所有
        // 子資源外連，sandbox 令腳本、表單、同源存取全失效；
        // 兩者都不影響圖片以 <img src> 正常顯示（該路徑是子資源載入）。
        response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");

        filterChain.doFilter(request, response);
    }
}
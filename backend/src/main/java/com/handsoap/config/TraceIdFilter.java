package com.handsoap.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 為每個請求生成 traceId，寫入 MDC 與 {@code X-Trace-Id} 回應標頭（review M1）。
 *
 * <p>用途：錯誤回應會帶 {@code traceId}（見 {@code GlobalExceptionHandler}），
 * 而 {@code logging.pattern.level} 會把 {@code [traceId:...]} 印在每一列日誌上；
 * 兩端比對即可在「例外細節只記日誌、不對外揭露」的前提下定位完整堆疊。</p>
 *
 * <p>排序刻意用 {@link Ordered#HIGHEST_PRECEDENCE}：Security 鏈的 order 是 -100，
 * 只有排在它之前，驗證失敗、CORS 拒絕等「進不到 Controller」的日誌才會帶 traceId。</p>
 *
 * <p>{@code X-Request-Id} 是外部輸入：只接受 {@code [A-Za-z0-9-]{1,32}}，
 * 不合格就忽略並改生成 —— 放行任意字元等於開了 log injection 的門
 * （夾帶換行即可偽造日誌列）。</p>
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter implements Ordered {

    /** MDC key；{@code logging.pattern.level} 的 {@code %X{traceId}} 指的就是它。 */
    public static final String MDC_KEY = "traceId";

    /** 來源標頭（常見於 nginx / API gateway 轉發）。 */
    public static final String REQUEST_HEADER = "X-Request-Id";

    /** 回應標頭：讓使用者回報問題時可直接附上這個 id。 */
    public static final String RESPONSE_HEADER = "X-Trace-Id";

    /** 外部可沿用的 id 規則：長度上界 32，並限英數字與連字號。 */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[A-Za-z0-9-]{1,32}$");

    /** 生成的 id 取 UUID 前 16 碼（去掉連字號），單機日誌內已足夠唯一。 */
    private static final int GENERATED_LENGTH = 16;

    @Override
    public int getOrder() {
        // 必須早於 Security 鏈（spring.security.filter.order 預設 -100）
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 取得目前請求的 traceId；MDC 無值時（背景執行、單元測試直接呼叫 handler）
     * 回傳新生成的值，確保日誌與回應永遠有關聯鍵。
     */
    public static String currentTraceId() {
        String existing = MDC.get(MDC_KEY);
        return existing != null && !existing.isBlank() ? existing : generateTraceId();
    }

    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, GENERATED_LENGTH);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String traceId = resolveTraceId(request);
        MDC.put(MDC_KEY, traceId);
        // 排查時用回應標頭比翻日誌快：回報錯誤只要附 X-Trace-Id 即可
        response.setHeader(RESPONSE_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 終端式必須清：MDC 是 ThreadLocal，不清會汙染連接池上的下一個請求
            MDC.remove(MDC_KEY);
        }
    }

    /** 外部值合格就沿用（代理鏈可貫穿同一 id），否則生成新的。 */
    private String resolveTraceId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_HEADER);
        if (incoming != null && SAFE_TRACE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return generateTraceId();
    }
}

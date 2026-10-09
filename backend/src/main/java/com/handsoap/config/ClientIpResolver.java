package com.handsoap.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 取出用戶端 IP（限流 key 的共用元件）。
 *
 * <p>優先取 {@code X-Forwarded-For} 的<b>第一段</b>（最靠近客戶端的那個），
 * 因為經過 nginx 反代時 {@code getRemoteAddr()} 拿到的是 nginx 的內部 IP，
 * 若用它當限流 key，<b>所有使用者會共用同一個計數器</b> —— 等於全站只能嘗試幾次。</p>
 *
 * <p>安全提醒：{@code X-Forwarded-For} 可被客戶端偽造，這裡信任它的前提是
 * 後端不直接對外（docker-compose 已刻意不發布 ports）。若日後後端開放對外，
 * 必須改為信任來自已知名單 proxy 的標頭，或直接採用 {@code getRemoteAddr()}。</p>
 *
 * <p>由 {@code AuthController}（登入限流）與 {@code PublicContactController}
 * （聯絡表單限流）共用 —— 兩處若各寫一份，日後修正偽造標頭的處理時
 * 極易只改一邊，造成兩套限流用不同的 key 語意。</p>
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            String first = comma > 0 ? forwardedFor.substring(0, comma) : forwardedFor;
            if (!first.isBlank()) {
                return first.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
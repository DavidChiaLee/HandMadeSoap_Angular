package com.handsoap.config;

import com.handsoap.entity.Admin;
import com.handsoap.repository.AdminRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtUtil jwtUtil;
    private final AdminRepository adminRepository;

    public JwtAuthFilter(
            JwtUtil jwtUtil,
            AdminRepository adminRepository
    ) {
        this.jwtUtil = jwtUtil;
        this.adminRepository = adminRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // 沒有 Authorization Header
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 取得 JWT
        String token = authHeader.substring(7);

        try {

            // 單次解析：簽章/格式/過期/username 都在 jwtUtil.parse 內一次搞定
            // （review L1：舊寫法對同一 token 重複 parseSignedClaims 4 次）。
            // parse 回 null = token 不可信 → 不建立 Authentication，
            // 受保護端點會由 SecurityContext 機制回 401。
            JwtUtil.ParsedToken parsed = jwtUtil.parse(token);

            // 如果目前沒有登入資訊
            if (parsed != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 查 DB 的唯一目的：撤銷檢查（tv 比對）＋ 取得 Admin 實體
                // 當 principal（AuthController.logout 依賴 principal instanceof Admin）。
                Admin admin = adminRepository
                        .findByUsername(parsed.username())
                        .orElse(null);

                // tv 不符 = token 已被撤銷（logout / 離職 revoke 會 bump
                // token_version）→ 不建立 Authentication。
                // 舊版 token 無 tv claim → parse 回 NO_TOKEN_VERSION(-1) → 必不符
                // → 全數失效（預期）。
                if (admin != null
                        && parsed.tokenVersion() == admin.getTokenVersion()) {

                    // 角色從 token claim 取用（review L1），不再靠 DB 的 admin.getRole()：
                    // token 由本系統簽發且已驗章，claim 內的 role 與簽發當下的 DB
                    // 值一致；角色變更本來就應該讓舊 token 失效（bump tv）。
                    // role claim 缺這（null）時退回 DB 值，避免舊 token 直接拿到
                    // "ROLE_null" 這種假權限。
                    String role = parsed.role() != null ? parsed.role() : admin.getRole();

                    // 建立角色
                    List<org.springframework.security.core.GrantedAuthority> authorities =
                            List.of(
                                    new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                            "ROLE_" + role
                                    )
                            );

                    // 建立 Authentication
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    admin,
                                    null,
                                    authorities
                            );

                    // 放入 SecurityContext
                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(auth);
                }
            }

        } catch (DataAccessException e) {

            // 只有 DB 例外會到這裡（review L2）。token 相關的過期/簽章/結構問題已由
            // jwtUtil.parse() 自行分類記錄並回 null，不會拋到這個 catch ——
            // 這是 L1 把解析集中到 parse() 的直接結果。
            //
            // 用 error 而非 debug：DB 不可用時撤銷檢查（tv 比對）做不了，此時若只留
            // debug，現場會是「全站 401 且日誌一片安靜」，完全無法判斷是攻擊還是故障。
            log.error("[jwt] 查詢 admin 失敗，無法完成撤銷檢查（DB 可能不可用）", e);
            SecurityContextHolder.clearContext();

        } catch (Exception e) {

            // 其餘未預期錯誤。同樣用 error —— 走到這裡代表不是已知的 DB 問題，
            // 靜默處理會讓問題在下次重現時依然無從追查。
            log.error("[jwt] 認證流程發生未預期錯誤", e);
            SecurityContextHolder.clearContext();
        }

        // 繼續 Filter Chain
        filterChain.doFilter(request, response);
    }
}
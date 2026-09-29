package com.handsoap.config;

import com.handsoap.entity.Admin;
import com.handsoap.repository.AdminRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

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

            // 從 JWT 取得 username
            String username = jwtUtil.extractUsername(token);

            // 如果目前沒有登入資訊
            if (username != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 從資料庫取得 Admin
                Admin admin = adminRepository
                        .findByUsername(username)
                        .orElse(null);

                // Admin 存在，而且 JWT 驗證成功
                if (admin != null && jwtUtil.validateToken(token, username)) {

                    // 建立角色
                    List<org.springframework.security.core.GrantedAuthority> authorities =
                            List.of(
                                    new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                            "ROLE_" + admin.getRole()
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

        } catch (Exception e) {

            // JWT 驗證失敗
            SecurityContextHolder.clearContext();
        }

        // 繼續 Filter Chain
        filterChain.doFilter(request, response);
    }
}
package com.handsoap.config;

import com.handsoap.entity.Admin;
import com.handsoap.repository.AdminRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private FilterChain filterChain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtUtil, adminRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("沒有 Authorization header 時應直接放行")
    void shouldPassThroughWithoutHeader() throws Exception {
        runFilter(requestWithoutToken());

        verify(jwtUtil, never()).extractUsername(anyString());
        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("header 格式非 Bearer 時應直接放行")
    void shouldPassThroughWhenNotBearer() throws Exception {
        MockHttpServletRequest request = requestWithoutToken();
        request.addHeader("Authorization", "Basic abc123");

        runFilter(request);

        verify(jwtUtil, never()).extractUsername(anyString());
        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("有效 token 且帳號存在時應建立帶 ROLE_ 的 Authentication")
    void shouldAuthenticateValidToken() throws Exception {
        MockHttpServletRequest request = requestWithToken("valid-token");
        given(jwtUtil.extractUsername("valid-token")).willReturn("admin");
        given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin("admin")));
        given(jwtUtil.validateToken("valid-token", "admin")).willReturn(true);
        // admin() 的 tokenVersion = 0（builder 預設）→ tv 相符
        given(jwtUtil.extractTokenVersion("valid-token")).willReturn(0);

        runFilter(request);

        Authentication auth = currentAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(Admin.class);
        assertThat(auth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("tv 與 DB tokenVersion 不符（已撤銷）時不應建立 Authentication（review M8 Phase 1）")
    void shouldNotAuthenticateWhenTokenVersionMismatch() throws Exception {
        MockHttpServletRequest request = requestWithToken("stale-token");
        given(jwtUtil.extractUsername("stale-token")).willReturn("admin");
        // DB 仍是 0，token 帶 tv=3 → 代表 logout 已把 token_version bump 過
        given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin("admin")));
        given(jwtUtil.validateToken("stale-token", "admin")).willReturn(true);
        given(jwtUtil.extractTokenVersion("stale-token")).willReturn(3);

        runFilter(request);

        // 不建立 Authentication → 受保護端點由 SecurityContext 機制回 401
        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("無 tv claim 的舊版 token（extract 回 -1）不應通過（review M8）")
    void shouldNotAuthenticateWhenTokenVersionClaimMissing() throws Exception {
        MockHttpServletRequest request = requestWithToken("legacy-token");
        given(jwtUtil.extractUsername("legacy-token")).willReturn("admin");
        given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin("admin")));
        given(jwtUtil.validateToken("legacy-token", "admin")).willReturn(true);
        // -1 ≠ 0：Phase 1 上線前簽發的舊 token 全數失效（預期行為）
        given(jwtUtil.extractTokenVersion("legacy-token")).willReturn(-1);

        runFilter(request);

        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("token 有效但資料庫查無此帳號時不應建立 Authentication")
    void shouldNotAuthenticateWhenAdminMissing() throws Exception {
        MockHttpServletRequest request = requestWithToken("orphan-token");
        given(jwtUtil.extractUsername("orphan-token")).willReturn("ghost");
        given(adminRepository.findByUsername("ghost")).willReturn(Optional.empty());

        runFilter(request);

        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("JWT 驗證失敗時不應建立 Authentication")
    void shouldNotAuthenticateWhenValidationFails() throws Exception {
        MockHttpServletRequest request = requestWithToken("tampered-token");
        given(jwtUtil.extractUsername("tampered-token")).willReturn("admin");
        given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin("admin")));
        given(jwtUtil.validateToken("tampered-token", "admin")).willReturn(false);

        runFilter(request);

        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("已有登入狀態時不應被覆蓋，也不應再查資料庫")
    void shouldNotOverrideExistingAuthentication() throws Exception {
        Authentication existing = new UsernamePasswordAuthenticationToken("existing", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        given(jwtUtil.extractUsername("second-token")).willReturn("admin");

        runFilter(requestWithToken("second-token"));

        assertThat(currentAuthentication()).isSameAs(existing);
        verify(adminRepository, never()).findByUsername(anyString());
    }

    @Test
    @DisplayName("處理過程拋出例外時應清空 SecurityContext，但仍繼續 filter chain")
    void shouldClearContextOnException() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("existing", null, List.of()));
        given(jwtUtil.extractUsername("boom-token")).willThrow(new IllegalStateException("jwt error"));

        runFilter(requestWithToken("boom-token"));

        assertThat(currentAuthentication()).isNull();
    }

    private MockHttpServletRequest requestWithoutToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/stats");
        return request;
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = requestWithoutToken();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private void runFilter(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), filterChain);
        verify(filterChain).doFilter(eq(request), any(MockHttpServletResponse.class));
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Admin admin(String username) {
        return Admin.builder().id(1L).username(username).role("ADMIN").build();
    }
}
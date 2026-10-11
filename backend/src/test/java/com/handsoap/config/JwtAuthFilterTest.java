package com.handsoap.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
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

    /** L2 測試用：掛在 JwtAuthFilter logger 上擷取事件。 */
    private ListAppender<ILoggingEvent> logAppender;

    /** 掛上 appender 前的原始等級，tearDown 要還原（logger 是 static，會跨測試殘留）。 */
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtUtil, adminRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        detachLogAppender();
    }

    /**
     * 掛上 ListAppender 並把等級壓到 DEBUG（理由同 {@code JwtUtilTest.captureLogs()}）。
     * 這裡主要用來斷言 {@code log.error}，但保留 DEBUG 是為了日後擴充分支時不必再改基礎設施。
     */
    private ListAppender<ILoggingEvent> captureLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(JwtAuthFilter.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        return logAppender;
    }

    private void detachLogAppender() {
        if (logAppender != null) {
            Logger logger = (Logger) LoggerFactory.getLogger(JwtAuthFilter.class);
            logger.detachAppender(logAppender);
            if (originalLevel != null) {
                logger.setLevel(originalLevel);
            }
            logAppender.stop();
            logAppender = null;
        }
    }

    @Test
    @DisplayName("沒有 Authorization header 時應直接放行")
    void shouldPassThroughWithoutHeader() throws Exception {
        runFilter(requestWithoutToken());

        verify(jwtUtil, never()).parse(anyString());
        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("header 格式非 Bearer 時應直接放行")
    void shouldPassThroughWhenNotBearer() throws Exception {
        MockHttpServletRequest request = requestWithoutToken();
        request.addHeader("Authorization", "Basic abc123");

        runFilter(request);

        verify(jwtUtil, never()).parse(anyString());
        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("有效 token 且帳號存在時應建立帶 ROLE_ 的 Authentication")
    void shouldAuthenticateValidToken() throws Exception {
        MockHttpServletRequest request = requestWithToken("valid-token");
        given(jwtUtil.parse("valid-token")).willReturn(parsedToken("admin", "ADMIN", 0));
        // admin() 的 tokenVersion = 0（builder 預設）→ tv 相符
        given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin("admin")));

        runFilter(request);

        Authentication auth = currentAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(Admin.class);
        assertThat(auth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("角色應取自 token claim 而非 DB 的 role（review L1）")
    void shouldUseRoleFromTokenClaim() throws Exception {
        // DB 的 role 是 EDITOR，但 token 帶的 role claim 是 ADMIN ——
        // 授權應以 token claim 為準（角色變更本應 bump tv 讓舊 token 失效）。
        MockHttpServletRequest request = requestWithToken("role-token");
        given(jwtUtil.parse("role-token")).willReturn(parsedToken("admin", "ADMIN", 0));
        given(adminRepository.findByUsername("admin"))
                .willReturn(Optional.of(admin("admin", "EDITOR")));

        runFilter(request);

        assertThat(currentAuthentication().getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("token 無 role claim 時角色應退回 DB 值（避免 ROLE_null）")
    void shouldFallBackToDatabaseRoleWhenClaimMissing() throws Exception {
        MockHttpServletRequest request = requestWithToken("no-role-token");
        given(jwtUtil.parse("no-role-token")).willReturn(parsedToken("admin", null, 0));
        given(adminRepository.findByUsername("admin"))
                .willReturn(Optional.of(admin("admin", "EDITOR")));

        runFilter(request);

        assertThat(currentAuthentication().getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_EDITOR");
    }

    @Test
    @DisplayName("token 解析失敗（簽章錯/過期）時不應建立 Authentication")
    void shouldNotAuthenticateWhenParseFails() throws Exception {
        MockHttpServletRequest request = requestWithToken("tampered-token");
        given(jwtUtil.parse("tampered-token")).willReturn(null);

        runFilter(request);

        assertThat(currentAuthentication()).isNull();
        verify(adminRepository, never()).findByUsername(anyString());
    }

    @Test
    @DisplayName("tv 與 DB tokenVersion 不符（已撤銷）時不應建立 Authentication（review M8 Phase 1）")
    void shouldNotAuthenticateWhenTokenVersionMismatch() throws Exception {
        MockHttpServletRequest request = requestWithToken("stale-token");
        given(jwtUtil.parse("stale-token")).willReturn(parsedToken("admin", "ADMIN", 3));
        // DB 仍是 0，token 帶 tv=3 → 代表 logout 已把 token_version bump 過
        given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin("admin")));

        runFilter(request);

        // 不建立 Authentication → 受保護端點由 SecurityContext 機制回 401
        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("無 tv claim 的舊版 token（parse 回 -1）不應通過（review M8）")
    void shouldNotAuthenticateWhenTokenVersionClaimMissing() throws Exception {
        MockHttpServletRequest request = requestWithToken("legacy-token");
        // -1 ≠ 0：Phase 1 上線前簽發的舊 token 全數失效（預期行為）
        given(jwtUtil.parse("legacy-token"))
                .willReturn(parsedToken("admin", "ADMIN", JwtUtil.NO_TOKEN_VERSION));
        given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin("admin")));

        runFilter(request);

        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("token 有效但資料庫查無此帳號時不應建立 Authentication")
    void shouldNotAuthenticateWhenAdminMissing() throws Exception {
        MockHttpServletRequest request = requestWithToken("orphan-token");
        given(jwtUtil.parse("orphan-token")).willReturn(parsedToken("ghost", "ADMIN", 0));
        given(adminRepository.findByUsername("ghost")).willReturn(Optional.empty());

        runFilter(request);

        assertThat(currentAuthentication()).isNull();
    }

    @Test
    @DisplayName("已有登入狀態時不應被覆蓋，也不應再查資料庫")
    void shouldNotOverrideExistingAuthentication() throws Exception {
        Authentication existing = new UsernamePasswordAuthenticationToken("existing", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        given(jwtUtil.parse("second-token")).willReturn(parsedToken("admin", "ADMIN", 0));

        runFilter(requestWithToken("second-token"));

        assertThat(currentAuthentication()).isSameAs(existing);
        verify(adminRepository, never()).findByUsername(anyString());
    }

    @Test
    @DisplayName("處理過程拋出例外時應清空 SecurityContext，但仍繼續 filter chain")
    void shouldClearContextOnException() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("existing", null, List.of()));
        given(jwtUtil.parse("boom-token")).willThrow(new IllegalStateException("jwt error"));

        runFilter(requestWithToken("boom-token"));

        assertThat(currentAuthentication()).isNull();
    }

    /**
     * DB 查詢失敗必須留下 error 級紀錄（review L2）。
     *
     * <p>這是 L1 之後 filter 唯一還會拋例外的路徑 —— token 的過期/簽章問題已由
     * {@code JwtUtil.parse()} 內部分類記錄並回 null，不會傳到這裡。若 DB 故障也只用
     * debug 記錄，現場會是「全站 401 且日誌一片安靜」，無法分辨是攻擊還是基礎設施掛了。</p>
     */
    @Test
    @DisplayName("DB 查詢失敗應記 error 級日誌、清空 context，並繼續 filter chain（review L2）")
    void shouldLogDatabaseFailureAtErrorLevel() throws Exception {
        ListAppender<ILoggingEvent> appender = captureLogs();
        given(jwtUtil.parse("db-down-token")).willReturn(parsedToken("admin", "ADMIN", 0));
        given(adminRepository.findByUsername("admin"))
                .willThrow(new DataAccessResourceFailureException("connection refused"));

        runFilter(requestWithToken("db-down-token"));

        assertThat(currentAuthentication()).isNull();
        assertThat(appender.list)
                .as("DB 不可用屬基礎設施故障，必須是可被 grep 到的 ERROR")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).contains("撤銷檢查");
                });
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

    private JwtUtil.ParsedToken parsedToken(String username, String role, int tokenVersion) {
        return new JwtUtil.ParsedToken(username, role, tokenVersion);
    }

    private Admin admin(String username) {
        return admin(username, "ADMIN");
    }

    private Admin admin(String username, String role) {
        return Admin.builder().id(1L).username(username).role(role).build();
    }
}
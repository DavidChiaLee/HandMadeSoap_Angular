package com.handsoap.service;

import com.handsoap.config.JwtUtil;
import com.handsoap.dto.LoginRequest;
import com.handsoap.entity.Admin;
import com.handsoap.repository.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private JwtUtil jwtUtil;

    private PasswordEncoder passwordEncoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        service = new AuthService(authenticationManager, adminRepository, passwordEncoder, jwtUtil);
    }

    @Test
    @DisplayName("登入成功時應回傳 token 與角色，並更新最後登入時間")
    void shouldReturnTokenAndUpdateLastLogin() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("admin", null, java.util.List.of());
        given(authenticationManager.authenticate(any())).willReturn(authentication);
        given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin()));
        given(jwtUtil.generateToken("admin", "ADMIN", 0)).willReturn("signed-jwt");

        var response = service.login(loginRequest("admin", "secret"));

        assertThat(response.getAccessToken()).isEqualTo("signed-jwt");
        assertThat(response.getUsername()).isEqualTo("admin");
        assertThat(response.getRole()).isEqualTo("ADMIN");
        then(adminRepository).should().save(any(Admin.class));
    }

    @Test
    @DisplayName("登入成功後 lastLoginAt 應被寫入")
    void shouldPopulateLastLoginAt() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("admin", null, java.util.List.of());
        given(authenticationManager.authenticate(any())).willReturn(authentication);
        given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin()));
        given(jwtUtil.generateToken(any(), any(), anyInt())).willReturn("signed-jwt");

        service.login(loginRequest("admin", "secret"));

        org.mockito.ArgumentCaptor<Admin> captor = org.mockito.ArgumentCaptor.forClass(Admin.class);
        then(adminRepository).should().save(captor.capture());
        assertThat(captor.getValue().getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("認證通過但資料庫查無帳號時應拋出例外")
    void shouldThrowWhenAdminMissingAfterAuthentication() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("ghost", null, java.util.List.of());
        given(authenticationManager.authenticate(any())).willReturn(authentication);
        given(adminRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(loginRequest("ghost", "secret")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("認證失敗時不應再查詢或寫入資料庫")
    void shouldNotTouchDatabaseWhenAuthenticationFails() {
        given(authenticationManager.authenticate(any()))
                .willThrow(new org.springframework.security.authentication.BadCredentialsException("bad"));

        assertThatThrownBy(() -> service.login(loginRequest("admin", "wrong")))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);

        then(adminRepository).should(never()).findByUsername(any());
        then(adminRepository).should(never()).save(any(Admin.class));
    }

    @Test
    @DisplayName("應以帶入的帳密建立認證請求")
    void shouldAuthenticateWithProvidedCredentials() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("admin", null, java.util.List.of());
        given(authenticationManager.authenticate(any())).willReturn(authentication);
        given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin()));
        given(jwtUtil.generateToken(any(), any(), anyInt())).willReturn("token");

        service.login(loginRequest("admin", "my-password"));

        org.mockito.ArgumentCaptor<org.springframework.security.authentication.UsernamePasswordAuthenticationToken> captor =
                org.mockito.ArgumentCaptor.forClass(
                        org.springframework.security.authentication.UsernamePasswordAuthenticationToken.class);
        then(authenticationManager).should().authenticate(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isEqualTo("admin");
        assertThat(captor.getValue().getCredentials()).isEqualTo("my-password");
    }

    /**
     * logout 應將 token_version +1 並存回 DB（review M8 Phase 1）。
     *
     * <p>這是「登出後未過期 token 立即失效」的根源：JwtAuthFilter 比對
     * tv claim 與此欄位，不符即不放行。bump 錯、漏存都會讓撤銷形同虛設。</p>
     */
    @Test
    @DisplayName("logout 應將 token_version +1 並寫入（review M8 Phase 1）")
    void shouldBumpTokenVersionOnLogout() {
        given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin()));

        service.logout("admin");

        org.mockito.ArgumentCaptor<Admin> captor =
                org.mockito.ArgumentCaptor.forClass(Admin.class);
        then(adminRepository).should().save(captor.capture());
        // admin() 建立時 tokenVersion = 0 → logout 後必須是 1
        assertThat(captor.getValue().getTokenVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("logout 查無帳號時應拋例外，不應默默 bump 別人")
    void shouldThrowWhenLogoutAdminMissing() {
        given(adminRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.logout("ghost"))
                .isInstanceOf(RuntimeException.class);

        then(adminRepository).should(never()).save(any(Admin.class));
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private Admin admin() {
        return Admin.builder()
                .id(1L)
                .username("admin")
                .passwordHash("$2a$10$hashed")
                .email("admin@example.com")
                .role("ADMIN")
                .build();
    }
}
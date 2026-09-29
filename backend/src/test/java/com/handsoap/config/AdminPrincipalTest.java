package com.handsoap.config;

import com.handsoap.entity.Admin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPrincipalTest {

    private Admin admin;
    private AdminPrincipal principal;

    @BeforeEach
    void setUp() {
        admin = Admin.builder()
                .id(1L)
                .username("admin")
                .passwordHash("$2a$10$hashed")
                .email("admin@example.com")
                .role("ADMIN")
                .build();
        principal = new AdminPrincipal(admin);
    }

    @Test
    @DisplayName("authorities 應為 ROLE_ 前綴的角色")
    void shouldExposeRolePrefixedAuthority() {
        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("username 與 password 應取自 Admin 實體")
    void shouldExposeCredentials() {
        assertThat(principal.getUsername()).isEqualTo("admin");
        assertThat(principal.getPassword()).isEqualTo("$2a$10$hashed");
    }

    @Test
    @DisplayName("getAdmin() 應取得原始 Admin 實體")
    void shouldExposeAdmin() {
        assertThat(principal.getAdmin()).isSameAs(admin);
    }

    @Test
    @DisplayName("帳號狀態旗標目前一律為可用")
    void shouldAlwaysBeActive() {
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
    }

    @Test
    @DisplayName("以 null 的 Admin 構造時，authorities 應反映 null 角色")
    void shouldHandleNullRoleSafely() {
        Admin nullRoleAdmin = new Admin();
        ReflectionTestUtils.setField(nullRoleAdmin, "role", null);

        AdminPrincipal nullPrincipal = new AdminPrincipal(nullRoleAdmin);

        assertThat(nullPrincipal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_null");
    }
}
package com.handsoap.config;

import com.handsoap.entity.Admin;
import com.handsoap.repository.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AdminUserDetailsServiceTest {

    @Mock
    private AdminRepository adminRepository;

    private AdminUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserDetailsService(adminRepository);
    }

    @Test
    @DisplayName("查無此帳號時應拋出 UsernameNotFoundException")
    void shouldThrowWhenAdminNotFound() {
        given(adminRepository.findByUsername("nobody")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("nobody");
    }

    @Test
    @DisplayName("查得到帳號時應回傳包裝好的 AdminPrincipal")
    void shouldReturnAdminPrincipal() {
        Admin admin = Admin.builder()
                .id(1L)
                .username("admin")
                .passwordHash("$2a$10$hashed")
                .email("admin@example.com")
                .role("ADMIN")
                .build();
        given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin));

        UserDetails details = service.loadUserByUsername("admin");

        assertThat(details).isInstanceOf(AdminPrincipal.class);
        assertThat(details.getUsername()).isEqualTo("admin");
        assertThat(details.getPassword()).isEqualTo("$2a$10$hashed");
        assertThat(details.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("應以帶入的 username 查詢資料庫")
    void shouldQueryRepositoryWithUsername() {
        Admin admin = Admin.builder().id(1L).username("staff").role("ADMIN").build();
        given(adminRepository.findByUsername("staff")).willReturn(Optional.of(admin));

        service.loadUserByUsername("staff");

        then(adminRepository).should().findByUsername("staff");
    }
}
package com.handsoap.service;

import com.handsoap.dto.CreateAdminRequest;
import com.handsoap.entity.Admin;
import com.handsoap.exception.BadRequestException;
import com.handsoap.repository.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AdminRepository adminRepository;

    private PasswordEncoder passwordEncoder;
    private AdminService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        service = new AdminService(adminRepository, passwordEncoder);
    }

    @Test
    @DisplayName("username 重複時應拒絕")
    void shouldRejectDuplicateUsername() {
        given(adminRepository.existsByUsername("dup")).willReturn(true);

        assertThatThrownBy(() -> service.createAdmin(
                request("dup", "dup@example.com", "secret", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Username already exists");
    }

    @Test
    @DisplayName("email 重複時應拒絕")
    void shouldRejectDuplicateEmail() {
        given(adminRepository.existsByUsername("fresh")).willReturn(false);
        given(adminRepository.existsByEmail("dup@example.com")).willReturn(true);

        assertThatThrownBy(() -> service.createAdmin(
                request("fresh", "dup@example.com", "secret", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email already exists");
    }

    @Test
    @DisplayName("role 未指定時應預設為 ADMIN")
    void shouldDefaultRoleToAdmin() {
        given(adminRepository.existsByUsername("n")).willReturn(false);
        given(adminRepository.existsByEmail("n@example.com")).willReturn(false);
        given(adminRepository.save(any(Admin.class))).willAnswer(inv -> withId(inv.getArgument(0), 3L));

        assertThat(service.createAdmin(request("n", "n@example.com", "secret", null)).getRole())
                .isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("role 非 ADMIN 時應拒絕")
    void shouldRejectNonAdminRole() {
        given(adminRepository.existsByUsername("u")).willReturn(false);
        given(adminRepository.existsByEmail("u@example.com")).willReturn(false);

        assertThatThrownBy(() -> service.createAdmin(
                request("u", "u@example.com", "secret", "USER")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Role must be ADMIN");
    }

    @Test
    @DisplayName("密碼應經過編碼後才寫入，且不得是明文")
    void shouldEncodePasswordBeforeSaving() {
        given(adminRepository.existsByUsername("enc")).willReturn(false);
        given(adminRepository.existsByEmail("enc@example.com")).willReturn(false);
        given(adminRepository.save(any(Admin.class))).willAnswer(inv -> withId(inv.getArgument(0), 4L));

        service.createAdmin(request("enc", "enc@example.com", "plain-text-pwd", null));

        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("plain-text-pwd");
        assertThat(passwordEncoder.matches("plain-text-pwd", captor.getValue().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("username 與 email 應去除前後空白後儲存")
    void shouldTrimUsernameAndEmail() {
        given(adminRepository.existsByUsername(anyString())).willReturn(false);
        given(adminRepository.existsByEmail(anyString())).willReturn(false);
        given(adminRepository.save(any(Admin.class))).willAnswer(inv -> withId(inv.getArgument(0), 5L));

        service.createAdmin(request("  spaced  ", "  spaced@example.com  ", "secret", null));

        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("spaced");
        assertThat(captor.getValue().getEmail()).isEqualTo("spaced@example.com");
    }

    /**
     * 守衛 review C4 的修正：授權已完全交給 SecurityConfig 的
     * {@code hasRole("ADMIN")}，Service 層不再查詢 admins 表筆數。
     *
     * <p>舊版這裡會呼叫 {@code adminRepository.count()}，屬 TOCTOU 競態來源；
     * 若未來有人把它加回來，這個測試會失敗。</p>
     */
    @Test
    @DisplayName("建立管理員時不應再查詢 admins 表筆數（TOCTOU 來源已移除）")
    void shouldNotQueryAdminCountWhenCreatingAdmin() {
        given(adminRepository.existsByUsername("noCount")).willReturn(false);
        given(adminRepository.existsByEmail("nocount@example.com")).willReturn(false);
        given(adminRepository.save(any(Admin.class))).willAnswer(inv -> withId(inv.getArgument(0), 6L));

        service.createAdmin(request("noCount", "nocount@example.com", "secret", null));

        verify(adminRepository, never()).count();
    }

    private CreateAdminRequest request(String username, String email, String password, String role) {
        CreateAdminRequest request = new CreateAdminRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        request.setRole(role);
        return request;
    }

    private Admin withId(Admin admin, long id) {
        admin.setId(id);
        return admin;
    }
}
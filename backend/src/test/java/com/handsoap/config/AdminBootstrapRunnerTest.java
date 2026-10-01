package com.handsoap.config;

import com.handsoap.entity.Admin;
import com.handsoap.repository.AdminRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 首位管理員 bootstrap 的行為驗證（review C4）。
 *
 * <p>最重要的兩個案例是「建立」與「不覆蓋既有帳號」：
 * 本機 {@code mysql/init/05-restore-seed.sh} 會先還原含管理員的備份，
 * 若 Runner 沒有正確跳過，會靜默建立第二個預設管理員。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    private static final DefaultApplicationArguments ARGS = new DefaultApplicationArguments();

    @Mock
    private AdminRepository adminRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("admins 表為空且變數齊全時應建立首位 ADMIN")
    void shouldCreateFirstAdminWhenTableIsEmpty() {
        given(adminRepository.count()).willReturn(0L);

        runner("owner", "s3cret-pass", "owner@example.com").run(ARGS);

        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(captor.capture());

        Admin saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("owner");
        assertThat(saved.getRole()).isEqualTo("ADMIN");
        assertThat(saved.getEmail()).isEqualTo("owner@example.com");
        // 密碼必須是 BCrypt 雜湊，不能是明文
        assertThat(saved.getPasswordHash()).isNotEqualTo("s3cret-pass");
        assertThat(passwordEncoder.matches("s3cret-pass", saved.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("admins 表已有帳號時不得建立（避免覆蓋／新增預設管理員）")
    void shouldNotCreateWhenAdminsAlreadyExist() {
        given(adminRepository.count()).willReturn(1L);

        runner("owner", "s3cret-pass", "owner@example.com").run(ARGS);

        verify(adminRepository, never()).save(any(Admin.class));
    }

    @Test
    @DisplayName("未提供密碼時應跳過建立，且不拋出例外讓容器正常啟動")
    void shouldSkipWhenPasswordMissing() {
        given(adminRepository.count()).willReturn(0L);

        runner("owner", "   ", "owner@example.com").run(ARGS);

        verify(adminRepository, never()).save(any(Admin.class));
    }

    @Test
    @DisplayName("未提供 username 時應跳過建立")
    void shouldSkipWhenUsernameMissing() {
        given(adminRepository.count()).willReturn(0L);

        runner("", "s3cret-pass", "owner@example.com").run(ARGS);

        verify(adminRepository, never()).save(any(Admin.class));
    }

    @Test
    @DisplayName("未提供 email 時應建立，email 留空")
    void shouldAllowEmptyEmail() {
        given(adminRepository.count()).willReturn(0L);

        runner("owner", "s3cret-pass", "").run(ARGS);

        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isNull();
    }

    @Test
    @DisplayName("username 與 email 的前後空白應被去除")
    void shouldTrimUsernameAndEmail() {
        given(adminRepository.count()).willReturn(0L);

        runner("  owner  ", "s3cret-pass", "  owner@example.com  ").run(ARGS);

        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("owner");
        assertThat(captor.getValue().getEmail()).isEqualTo("owner@example.com");
    }

    private AdminBootstrapRunner runner(String username, String password, String email) {
        return new AdminBootstrapRunner(adminRepository, passwordEncoder, username, password, email);
    }
}
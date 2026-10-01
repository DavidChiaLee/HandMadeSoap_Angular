package com.handsoap.config;

import com.handsoap.entity.Admin;
import com.handsoap.repository.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 啟動時建立首位管理員（bootstrap），取代原本「admins 表為空時可匿名呼叫
 * POST /api/v1/admin/admins」的設計。
 *
 * <p>為什麼要改：舊做法把授權判斷延後到 Service 層的 {@code adminRepository.count() > 0}，
 * 而該端點在 SecurityConfig 是 {@code permitAll}，等於把「部署後、owner 建立帳號前」
 * 這段時間窗完全開放給公網 —— 任何人都能搶先建立 {@code role=ADMIN} 並接管後台。</p>
 *
 * <p>新做法把建立動作移回部署者手上：只有設定 {@code app.bootstrap.enabled=true}
 * 且 {@code admins} 表確實為空時才會建立，且建立完即可關閉開關。</p>
 *
 * <p>{@code @ConditionalOnProperty} 預設 matchIfMissing=false，因此未設定
 * {@code app.bootstrap.enabled} 時本 Runner 根本不會被載入 —— 預設即安全。</p>
 */
@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private static final String ROLE_ADMIN = "ADMIN";

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    private final String username;
    private final String rawPassword;
    private final String email;

    public AdminBootstrapRunner(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.username:}") String username,
            @Value("${app.bootstrap.password:}") String rawPassword,
            @Value("${app.bootstrap.email:}") String email) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.rawPassword = rawPassword;
        this.email = email;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 注意：本機 mysql/init/05-restore-seed.sh 會先還原含管理員的備份，
        // 這裡 count() > 0 就會跳過 —— 這正是期望行為，絕不可覆蓋既有帳號。
        if (adminRepository.count() > 0) {
            log.info("[bootstrap] admins 表已有帳號，略過建立首位管理員");
            return;
        }

        if (isBlank(username) || isBlank(rawPassword)) {
            // 不拋出例外：容器應照常啟動，只是沒有管理員可用。
            // 啟動失敗會讓部署者以為資料庫有問題，反而難以診斷。
            log.warn("[bootstrap] 已啟用但未提供 app.bootstrap.username / app.bootstrap.password，"
                    + "本次不會建立管理員。若確實需要建立首位管理員，請設定環境變數 "
                    + "BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD 後重新啟動，"
                    + "建立完成後請將 app.bootstrap.enabled 設回 false。");
            return;
        }

        Admin admin = Admin.builder()
                .username(username.trim())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .email(isBlank(email) ? null : email.trim())
                .role(ROLE_ADMIN)
                .build();

        adminRepository.save(admin);
        log.info("[bootstrap] 已建立首位管理員 username={}，請立即登入並更換密碼，"
                + "並將 app.bootstrap.enabled 設為 false。", admin.getUsername());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
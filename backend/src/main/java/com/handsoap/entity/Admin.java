package com.handsoap.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "admins")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 20)
    private String role;

    /**
     * token 撤銷版本號（review M8 Phase 1）。
     *
     * <p>每次 logout / revoke 就 +1；JWT 內的 {@code tv} claim 必須與此值相符才放行
     * （見 {@code JwtAuthFilter}）。刻意用 DB 欄位而非 Redis 黑名單：單實例、
     * admin 極少、filter 本來就查 Admin，成本趨近於零。</p>
     *
     * <p>粗粒度語意：+1 會讓該 admin 的<b>全部</b> token 同時失效
     * （含其他裝置與尚未過期的 refresh —— Phase 2 上線後一併受控）。</p>
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** <<< 新增：refresh token 雜湊值 */
    @Column(name = "refresh_token_hash", length = 128)
    private String refreshTokenHash;

    /** <<< 新增：refresh token 過期時間 */
    @Column(name = "refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

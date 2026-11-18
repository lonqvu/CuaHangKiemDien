package com.shop.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter @Setter
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String username;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Column(nullable = false)
    private String role; // ADMIN | STAFF
    private boolean active = true;

    // ----- xac thuc 2 lop (V6) -----
    /** Khoa TOTP da ma hoa (SecretBox); co khi dang cai dat (chua bat) hoac da bat. */
    @Column(name = "totp_secret")
    private String totpSecret;
    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;
    /** Buoc 30 giay cua ma TOTP dung gan nhat (chong dung lai ma). */
    @Column(name = "totp_last_step")
    private Long totpLastStep;
    /** Ban bam BCrypt cua ma du phong con lai, moi dong 1 ma. */
    @Column(name = "recovery_codes", columnDefinition = "text")
    private String recoveryCodes;
}

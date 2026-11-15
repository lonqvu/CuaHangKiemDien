package com.shop.config;

import com.shop.auth.AppUser;
import com.shop.auth.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Tao tai khoan admin lan dau chay. DOI MAT KHAU ngay (ADMIN_PASSWORD) khi deploy that. */
@Component
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public DataSeeder(AppUserRepository users, PasswordEncoder encoder,
                      @Value("${app.seed-admin.username}") String username,
                      @Value("${app.seed-admin.password}") String password) {
        this.users = users; this.encoder = encoder; this.username = username; this.password = password;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) return;
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        u.setFullName("Quan tri");
        u.setRole("ADMIN");
        users.save(u);
        log.warn("Da tao tai khoan admin '{}'. Hay doi mat khau mac dinh!", username);
    }
}

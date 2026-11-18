package com.shop.auth;

import com.shop.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginThrottle throttle;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, LoginThrottle throttle) {
        this.users = users; this.encoder = encoder; this.jwt = jwt; this.throttle = throttle;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String token, String username, String fullName, String role) {}

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest r, jakarta.servlet.http.HttpServletRequest req) {
        String ip = LoginThrottle.clientIp(req);
        throttle.check(ip, r.username()); // sai qua nhieu lan -> 429, khong kiem mat khau
        AppUser u = users.findByUsername(r.username())
                .filter(AppUser::isActive)
                .filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
                .orElse(null);
        if (u == null) {
            throttle.failed(ip, r.username());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sai tên đăng nhập hoặc mật khẩu, hoặc tài khoản đã bị khóa");
        }
        throttle.succeeded(ip, r.username());
        return new LoginResponse(jwt.generate(u), u.getUsername(), u.getFullName(), u.getRole());
    }
}

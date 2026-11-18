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
    private final TwoFactorService twoFactor;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, LoginThrottle throttle, TwoFactorService twoFactor) {
        this.users = users; this.encoder = encoder; this.jwt = jwt; this.throttle = throttle; this.twoFactor = twoFactor;
    }

    /** otp: ma 6 so tu app xac thuc (hoac ma du phong) - chi can khi tai khoan da bat xac thuc 2 lop. */
    public record LoginRequest(@NotBlank String username, @NotBlank String password, String otp) {}
    /**
     * otpRequired = true: mat khau dung nhung tai khoan bat 2 lop -> gui lai kem otp (chua co token).
     * twoFactor: tai khoan da bat 2 lop chua (giao dien nhac admin bat).
     */
    public record LoginResponse(String token, String username, String fullName, String role, boolean otpRequired, boolean twoFactor) {}

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
        if (u.isTotpEnabled()) {
            if (r.otp() == null || r.otp().isBlank()) return new LoginResponse(null, u.getUsername(), null, null, true, true);
            if (!twoFactor.verify(u, r.otp())) {
                throttle.failed(ip, r.username());
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Mã xác thực không đúng hoặc đã dùng. Nhập mã mới nhất trên ứng dụng xác thực.");
            }
        }
        throttle.succeeded(ip, r.username());
        return new LoginResponse(jwt.generate(u), u.getUsername(), u.getFullName(), u.getRole(), false, u.isTotpEnabled());
    }
}

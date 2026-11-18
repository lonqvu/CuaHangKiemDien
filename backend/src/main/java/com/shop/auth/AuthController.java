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

    /**
     * otp: ma 6 so tu app xac thuc (hoac ma du phong) - chi can khi tai khoan da bat xac thuc 2 lop.
     * remember: "Ghi nho dang nhap tren may nay" (phien 7 ngay thay vi 8 gio).
     * trustToken: may nay da duoc "tin cay" (khong hoi ma 2 lop) tu lan truoc; trustDevice: tin cay may nay tu lan nay.
     */
    public record LoginRequest(@NotBlank String username, @NotBlank String password, String otp,
                               Boolean remember, String trustToken, Boolean trustDevice) {}
    /**
     * otpRequired = true: mat khau dung nhung tai khoan bat 2 lop -> gui lai kem otp (chua co token).
     * twoFactor: tai khoan da bat 2 lop chua (giao dien nhac admin bat).
     */
    public record LoginResponse(String token, String username, String fullName, String role, boolean otpRequired, boolean twoFactor,
                                String trustToken) {}

    public record NewToken(String token) {}

    /**
     * "Dang xuat moi thiet bi khac": tang version tai khoan -> moi token cu (may khac, dien thoai bi mat...) het hieu luc,
     * ke ca "tin cay may"; may dang dung nhan token moi (cung kieu ghi nho / khong) nen khong bi dang xuat.
     */
    @PostMapping("/logout-others")
    @org.springframework.transaction.annotation.Transactional
    public NewToken logoutOthers(org.springframework.security.core.Authentication auth, jakarta.servlet.http.HttpServletRequest req) {
        AppUser u = users.findByUsername(auth.getName()).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        u.setTokenVersion(u.getTokenVersion() + 1);
        users.save(u);
        String h = req.getHeader("Authorization");
        return new NewToken(jwt.generate(u, h != null && jwt.remembered(h.substring(7))));
    }

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
        String trust = null;
        if (u.isTotpEnabled() && !jwt.validTrust(r.trustToken(), u)) { // may da tin cay -> khong hoi ma
            if (r.otp() == null || r.otp().isBlank()) return new LoginResponse(null, u.getUsername(), null, null, true, true, null);
            if (!twoFactor.verify(u, r.otp())) {
                throttle.failed(ip, r.username());
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Mã xác thực không đúng hoặc đã dùng. Nhập mã mới nhất trên ứng dụng xác thực.");
            }
            if (Boolean.TRUE.equals(r.trustDevice())) trust = jwt.generateTrust(u);
        } else if (u.isTotpEnabled()) {
            trust = r.trustToken(); // giu nguyen, het han theo ngay da cap
        }
        throttle.succeeded(ip, r.username());
        return new LoginResponse(jwt.generate(u, Boolean.TRUE.equals(r.remember())), u.getUsername(), u.getFullName(), u.getRole(),
                false, u.isTotpEnabled(), trust);
    }
}

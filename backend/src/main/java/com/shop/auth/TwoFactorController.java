package com.shop.auth;

import com.shop.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Tu cai dat xac thuc 2 lop cho tai khoan dang dang nhap (man Doi mat khau). */
@RestController
@RequestMapping("/api/auth/2fa")
public class TwoFactorController {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final TwoFactorService twoFactor;
    private final LoginThrottle throttle;

    public TwoFactorController(AppUserRepository users, PasswordEncoder encoder, TwoFactorService twoFactor, LoginThrottle throttle) {
        this.users = users; this.encoder = encoder; this.twoFactor = twoFactor; this.throttle = throttle;
    }

    public record Status(boolean enabled, int recoveryLeft) {}
    public record PasswordRequest(@NotBlank String password) {}
    public record CodeRequest(@NotBlank String code) {}
    public record DisableRequest(@NotBlank String password, @NotBlank String code) {}
    public record Enabled(List<String> recoveryCodes) {}

    private AppUser me(Authentication auth) {
        return users.findByUsername(auth.getName()).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
    }

    /** Mat khau dung moi cho cai / tat (dien thoai bi muon tam cung khong tu bat-tat duoc). Sai thi tinh vao gioi han do mat khau. */
    private void checkPassword(AppUser u, String password, HttpServletRequest req) {
        String ip = LoginThrottle.clientIp(req);
        throttle.check(ip, u.getUsername());
        if (!encoder.matches(password, u.getPasswordHash())) {
            throttle.failed(ip, u.getUsername());
            throw ApiException.badRequest("Mật khẩu không đúng");
        }
    }

    @GetMapping
    public Status status(Authentication auth) {
        AppUser u = me(auth);
        return new Status(u.isTotpEnabled(), twoFactor.recoveryLeft(u));
    }

    @PostMapping("/setup")
    public TwoFactorService.Setup setup(@Valid @RequestBody PasswordRequest r, Authentication auth, HttpServletRequest req) {
        AppUser u = me(auth);
        checkPassword(u, r.password(), req);
        return twoFactor.setup(u);
    }

    @PostMapping("/enable")
    public Enabled enable(@Valid @RequestBody CodeRequest r, Authentication auth) {
        return new Enabled(twoFactor.enable(me(auth), r.code()));
    }

    @PostMapping("/disable")
    public Status disable(@Valid @RequestBody DisableRequest r, Authentication auth, HttpServletRequest req) {
        AppUser u = me(auth);
        checkPassword(u, r.password(), req);
        if (!twoFactor.verify(u, r.code())) {
            throttle.failed(LoginThrottle.clientIp(req), u.getUsername());
            throw ApiException.badRequest("Mã xác thực không đúng");
        }
        twoFactor.disable(u);
        return new Status(false, 0);
    }
}

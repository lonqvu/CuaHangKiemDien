package com.shop.auth;

import com.shop.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Thay man Account (quan ly tai khoan) va ChangePassWord cua app cu. */
@RestController
@RequestMapping("/api")
public class UserController {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final LoginThrottle throttle;
    private final TwoFactorService twoFactor;
    private final JwtService jwt;

    public UserController(AppUserRepository users, PasswordEncoder encoder, LoginThrottle throttle, TwoFactorService twoFactor, JwtService jwt) {
        this.users = users; this.encoder = encoder; this.throttle = throttle; this.twoFactor = twoFactor; this.jwt = jwt;
    }

    /** Moi phien dang nhap cu cua tai khoan (moi may) het hieu luc, ke ca "tin cay may" cua 2 lop. */
    private static void revokeSessions(AppUser u) { u.setTokenVersion(u.getTokenVersion() + 1); }

    public record UserDto(Long id, String username, String fullName, String role, boolean active, boolean twoFactor) {}
    public record CreateUser(@NotBlank @Size(max = 50) String username, @NotBlank String fullName,
                             @NotBlank @Pattern(regexp = "ADMIN|STAFF") String role, @NotBlank @Size(min = 6) String password) {}
    public record UpdateUser(@NotBlank String fullName, @NotBlank @Pattern(regexp = "ADMIN|STAFF") String role, boolean active) {}
    public record ResetPassword(@NotBlank @Size(min = 6) String password) {}
    /** otp: can khi doi tu man dang nhap (chua dang nhap) ma tai khoan da bat xac thuc 2 lop. */
    public record ChangePassword(String username, @NotBlank String oldPassword, @NotBlank @Size(min = 6) String newPassword, String otp) {}

    private UserDto dto(AppUser u) {
        return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isActive(), u.isTotpEnabled());
    }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
    }

    @GetMapping("/users")
    public List<UserDto> list() {
        return users.findAll().stream().map(this::dto).toList();
    }

    @PostMapping("/users")
    public UserDto create(@Valid @RequestBody CreateUser r) {
        if (users.findByUsername(r.username().trim()).isPresent()) throw ApiException.conflict("Tên đăng nhập đã tồn tại");
        AppUser u = new AppUser();
        u.setUsername(r.username().trim());
        u.setFullName(r.fullName().trim());
        u.setRole(r.role());
        u.setPasswordHash(encoder.encode(r.password()));
        return dto(users.save(u));
    }

    @PutMapping("/users/{id}")
    @Transactional
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUser r, Authentication auth) {
        AppUser u = find(id);
        boolean losesAdmin = "ADMIN".equals(u.getRole()) && (!"ADMIN".equals(r.role()) || !r.active());
        if (losesAdmin && u.getUsername().equals(auth.getName()))
            throw ApiException.badRequest("Không thể tự hạ quyền hoặc tự khóa tài khoản của mình");
        if (losesAdmin && users.countByRoleAndActiveTrue("ADMIN") <= 1)
            throw ApiException.badRequest("Phải còn ít nhất 1 tài khoản quản trị đang dùng");
        u.setFullName(r.fullName().trim());
        u.setRole(r.role());
        u.setActive(r.active());
        return dto(u);
    }

    /** Quan tri tat xac thuc 2 lop giup (nguoi do mat dien thoai + mat ma du phong); ho tu bat lai sau khi dang nhap. */
    @PostMapping("/users/{id}/2fa/reset")
    @Transactional
    public UserDto resetTwoFactor(@PathVariable Long id) {
        AppUser u = find(id);
        twoFactor.disable(u);
        revokeSessions(u); // mat dien thoai: may dang dang nhap san tren dien thoai do cung bi dang xuat
        return dto(u);
    }

    @PostMapping("/users/{id}/reset-password")
    @Transactional
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPassword r) {
        AppUser u = find(id);
        u.setPasswordHash(encoder.encode(r.password()));
        revokeSessions(u); // mat khau cu (co the da lo) khong con phien nao dung duoc
    }

    /**
     * Doi mat khau (can mat khau cu). Giong app cu: lam duoc ngay tu man dang nhap (chon ten dang nhap)
     * hoac sau khi dang nhap (lay tai khoan dang dung).
     */
    @PostMapping("/auth/change-password")
    @Transactional
    public AuthController.NewToken changePassword(@Valid @RequestBody ChangePassword r, Authentication auth, jakarta.servlet.http.HttpServletRequest req) {
        String username = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())
                ? auth.getName() : r.username();
        if (username == null || username.isBlank()) throw ApiException.badRequest("Chưa nhập tên đăng nhập");
        // doi mat khau khong can dang nhap -> cung la cho do mat khau: chung gioi han voi dang nhap
        String ip = LoginThrottle.clientIp(req);
        throttle.check(ip, username);
        AppUser u = users.findByUsername(username.trim()).filter(AppUser::isActive).orElse(null);
        if (u == null || !encoder.matches(r.oldPassword(), u.getPasswordHash())) {
            throttle.failed(ip, username);
            throw ApiException.badRequest("Tên đăng nhập hoặc mật khẩu hiện tại không đúng");
        }
        // tai khoan bat 2 lop ma doi tu man dang nhap (chua dang nhap): can them ma - lo mat khau cung khong doi duoc
        boolean loggedIn = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName());
        if (u.isTotpEnabled() && !loggedIn && !twoFactor.verify(u, r.otp())) {
            throttle.failed(ip, username);
            throw ApiException.badRequest("Tài khoản đã bật xác thực 2 lớp: nhập mã xác thực (6 số) đúng");
        }
        throttle.succeeded(ip, username);
        u.setPasswordHash(encoder.encode(r.newPassword()));
        // Doi mat khau -> dang xuat moi may khac; may dang doi (neu dang dang nhap) nhan token moi de khong bi dang xuat
        revokeSessions(u);
        String h = req.getHeader("Authorization");
        return new AuthController.NewToken(loggedIn ? jwt.generate(u, h != null && jwt.remembered(h.substring(7))) : null);
    }
}

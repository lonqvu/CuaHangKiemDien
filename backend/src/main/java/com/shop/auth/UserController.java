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

    public UserController(AppUserRepository users, PasswordEncoder encoder, LoginThrottle throttle) {
        this.users = users; this.encoder = encoder; this.throttle = throttle;
    }

    public record UserDto(Long id, String username, String fullName, String role, boolean active) {}
    public record CreateUser(@NotBlank @Size(max = 50) String username, @NotBlank String fullName,
                             @NotBlank @Pattern(regexp = "ADMIN|STAFF") String role, @NotBlank @Size(min = 6) String password) {}
    public record UpdateUser(@NotBlank String fullName, @NotBlank @Pattern(regexp = "ADMIN|STAFF") String role, boolean active) {}
    public record ResetPassword(@NotBlank @Size(min = 6) String password) {}
    public record ChangePassword(String username, @NotBlank String oldPassword, @NotBlank @Size(min = 6) String newPassword) {}

    private UserDto dto(AppUser u) {
        return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isActive());
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

    @PostMapping("/users/{id}/reset-password")
    @Transactional
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPassword r) {
        find(id).setPasswordHash(encoder.encode(r.password()));
    }

    /**
     * Doi mat khau (can mat khau cu). Giong app cu: lam duoc ngay tu man dang nhap (chon ten dang nhap)
     * hoac sau khi dang nhap (lay tai khoan dang dung).
     */
    @PostMapping("/auth/change-password")
    @Transactional
    public void changePassword(@Valid @RequestBody ChangePassword r, Authentication auth, jakarta.servlet.http.HttpServletRequest req) {
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
        throttle.succeeded(ip, username);
        u.setPasswordHash(encoder.encode(r.newPassword()));
    }
}

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

    public UserController(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users; this.encoder = encoder;
    }

    public record UserDto(Long id, String username, String fullName, String role, boolean active) {}
    public record CreateUser(@NotBlank @Size(max = 50) String username, @NotBlank String fullName,
                             @NotBlank @Pattern(regexp = "ADMIN|STAFF") String role, @NotBlank @Size(min = 6) String password) {}
    public record UpdateUser(@NotBlank String fullName, @NotBlank @Pattern(regexp = "ADMIN|STAFF") String role, boolean active) {}
    public record ResetPassword(@NotBlank @Size(min = 6) String password) {}
    public record ChangePassword(@NotBlank String oldPassword, @NotBlank @Size(min = 6) String newPassword) {}

    private UserDto dto(AppUser u) {
        return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isActive());
    }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan"));
    }

    @GetMapping("/users")
    public List<UserDto> list() {
        return users.findAll().stream().map(this::dto).toList();
    }

    @PostMapping("/users")
    public UserDto create(@Valid @RequestBody CreateUser r) {
        if (users.findByUsername(r.username().trim()).isPresent()) throw ApiException.conflict("Ten dang nhap da ton tai");
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
            throw ApiException.badRequest("Khong the tu ha quyen hoac khoa chinh minh");
        if (losesAdmin && users.countByRoleAndActiveTrue("ADMIN") <= 1)
            throw ApiException.badRequest("Phai con it nhat 1 tai khoan quan tri");
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

    /** Ai dang nhap cung doi duoc mat khau cua minh (can mat khau cu). */
    @PostMapping("/auth/change-password")
    @Transactional
    public void changePassword(@Valid @RequestBody ChangePassword r, Authentication auth) {
        AppUser u = users.findByUsername(auth.getName()).orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan"));
        if (!encoder.matches(r.oldPassword(), u.getPasswordHash())) throw ApiException.badRequest("Mat khau cu khong dung");
        u.setPasswordHash(encoder.encode(r.newPassword()));
    }
}

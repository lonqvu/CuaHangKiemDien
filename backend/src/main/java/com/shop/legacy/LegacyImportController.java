package com.shop.legacy;

import com.shop.auth.AppUser;
import com.shop.auth.AppUserRepository;
import com.shop.auth.LoginThrottle;
import com.shop.auth.TwoFactorService;
import com.shop.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Nhap du lieu tu phan mem cu (chi quan tri: SecurityConfig anyRequest -> ADMIN).
 *  GET  /api/admin/legacy-import          : nhap duoc khong (database con trong?), lan nhap truoc
 *  POST /api/admin/legacy-import?dryRun=  : than request = file du-lieu-cu.json (hoac .json.gz)
 *        dryRun=true : nap + doi chieu roi HUY (xem bao cao truoc)
 *        dryRun=false: nap + doi chieu, KHOP HET moi luu; lech -> huy het
 *  POST /api/admin/legacy-import/reset    : xoa TOAN BO du lieu ban hang; can mat khau + ma xac thuc 2 lop
 */
@RestController
@RequestMapping("/api/admin/legacy-import")
public class LegacyImportController {
    private final LegacyImportService service;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final TwoFactorService twoFactor;
    private final LoginThrottle throttle;

    public LegacyImportController(LegacyImportService service, AppUserRepository users, PasswordEncoder encoder,
                                  TwoFactorService twoFactor, LoginThrottle throttle) {
        this.service = service; this.users = users; this.encoder = encoder; this.twoFactor = twoFactor; this.throttle = throttle;
    }

    @GetMapping
    public LegacyImportService.Status status() { return service.status(); }

    public record ResetRequest(String password, String code) {}

    /**
     * Xoa TOAN BO du lieu ban hang. Phai bat xac thuc 2 lop, nhap dung mat khau + ma 6 so (hoac ma du phong).
     * Sai tinh vao gioi han dang nhap sai (5 lan -> khoa tam) nhu dang nhap.
     */
    @PostMapping("/reset")
    public LegacyImportService.ResetResult reset(@RequestBody ResetRequest r, Authentication auth, HttpServletRequest req) {
        AppUser u = users.findByUsername(auth.getName()).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        if (!u.isTotpEnabled())
            throw ApiException.badRequest("Cần bật xác thực 2 lớp cho tài khoản này trước khi xóa dữ liệu (màn Đổi mật khẩu)");
        String ip = LoginThrottle.clientIp(req);
        throttle.check(ip, u.getUsername());
        if (r.password() == null || !encoder.matches(r.password(), u.getPasswordHash())) {
            throttle.failed(ip, u.getUsername());
            throw ApiException.badRequest("Mật khẩu không đúng");
        }
        if (!twoFactor.verify(u, r.code())) {
            throttle.failed(ip, u.getUsername());
            throw ApiException.badRequest("Mã xác thực không đúng");
        }
        throttle.succeeded(ip, u.getUsername());
        return service.resetAll(u.getUsername());
    }

    @PostMapping(consumes = MediaType.ALL_VALUE)
    public LegacyImportService.Result run(@RequestBody byte[] body, @RequestParam(defaultValue = "true") boolean dryRun,
                                          Authentication auth) {
        return service.run(body, dryRun, auth.getName());
    }
}

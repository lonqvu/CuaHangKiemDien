package com.shop.auth;

import com.shop.common.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Xac thuc 2 lop: cai dat (tao khoa, chua bat) -> bat (nhap dung 1 ma tu app) -> moi lan dang nhap can them ma 6 so
 * (hoac 1 ma du phong, moi ma dung 1 lan). Tat: tu tat (can mat khau + ma) hoac quan tri khac tat giup.
 */
@Service
public class TwoFactorService {
    static final String ISSUER = "Nhôm Sắt Kiểm Diên";
    static final int RECOVERY_COUNT = 8;
    private static final String RC_CHARS = "abcdefghjkmnpqrstuvwxyz23456789"; // bo chu de nham (0/o, 1/l/i)
    private static final SecureRandom RNG = new SecureRandom();

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final SecretBox box;
    private Clock clock = Clock.systemUTC();

    public TwoFactorService(AppUserRepository users, PasswordEncoder encoder, SecretBox box) {
        this.users = users; this.encoder = encoder; this.box = box;
    }

    void setClock(Clock c) { this.clock = c; } // test

    private long nowStep() { return Totp.stepAt(clock.instant().getEpochSecond()); }

    public record Setup(String secret, String otpauthUri) {}

    /** Tao khoa moi (chua bat). Goi lai = tao khoa khac (quet lai QR). */
    @Transactional
    public Setup setup(AppUser u) {
        if (u.isTotpEnabled()) throw ApiException.conflict("Tài khoản đã bật xác thực 2 lớp. Tắt trước nếu muốn cài lại.");
        String secret = Totp.newSecret();
        u.setTotpSecret(box.seal(secret));
        u.setTotpLastStep(null);
        users.save(u);
        return new Setup(secret, Totp.otpauthUri(ISSUER, u.getUsername(), secret));
    }

    /** Bat sau khi nhap dung ma tu app (chung to da quet dung). Tra ve ma du phong - CHI HIEN 1 LAN. */
    @Transactional
    public List<String> enable(AppUser u, String code) {
        if (u.isTotpEnabled()) throw ApiException.conflict("Tài khoản đã bật xác thực 2 lớp");
        if (u.getTotpSecret() == null) throw ApiException.badRequest("Chưa tạo mã QR. Bấm \"Bật xác thực 2 lớp\" trước.");
        long step = Totp.verify(box.open(u.getTotpSecret()), code, nowStep(), null);
        if (step < 0) throw ApiException.badRequest("Mã không đúng. Kiểm tra giờ trên điện thoại (tự động) rồi nhập mã mới nhất.");
        u.setTotpEnabled(true);
        u.setTotpLastStep(step);
        List<String> codes = newRecoveryCodes(u);
        users.save(u);
        return codes;
    }

    /** Kiem ma luc dang nhap / tat 2 lop: ma 6 so tu app, hoac ma du phong (dung xong la mat). */
    @Transactional
    public boolean verify(AppUser u, String code) {
        if (!u.isTotpEnabled() || code == null || code.isBlank()) return false;
        String c = code.trim();
        long step = Totp.verify(box.open(u.getTotpSecret()), c, nowStep(), u.getTotpLastStep());
        if (step >= 0) {
            u.setTotpLastStep(step);
            users.save(u);
            return true;
        }
        if (u.getRecoveryCodes() != null) {
            String norm = c.toLowerCase().replaceAll("[^a-z0-9]", "");
            List<String> left = new ArrayList<>(Arrays.asList(u.getRecoveryCodes().split("\n")));
            for (String h : left) {
                if (!h.isBlank() && encoder.matches(norm, h)) {
                    left.remove(h);
                    u.setRecoveryCodes(String.join("\n", left));
                    users.save(u);
                    return true;
                }
            }
        }
        return false;
    }

    @Transactional
    public void disable(AppUser u) {
        u.setTotpEnabled(false);
        u.setTotpSecret(null);
        u.setTotpLastStep(null);
        u.setRecoveryCodes(null);
        users.save(u);
    }

    public int recoveryLeft(AppUser u) {
        return u.getRecoveryCodes() == null || u.getRecoveryCodes().isBlank() ? 0 : u.getRecoveryCodes().split("\n").length;
    }

    private List<String> newRecoveryCodes(AppUser u) {
        List<String> plain = new ArrayList<>(), hashes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_COUNT; i++) {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < 8; k++) sb.append(RC_CHARS.charAt(RNG.nextInt(RC_CHARS.length())));
            String c = sb.toString();
            plain.add(c.substring(0, 4) + "-" + c.substring(4));
            hashes.add(encoder.encode(c));
        }
        u.setRecoveryCodes(String.join("\n", hashes));
        return plain;
    }
}

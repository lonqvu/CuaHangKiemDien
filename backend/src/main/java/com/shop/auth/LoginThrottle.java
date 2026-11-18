package com.shop.auth;

import com.shop.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chan do mat khau khi app chay tren Internet (VPS): dang nhap / doi mat khau (khong can dang nhap) sai nhieu lan -> tam khoa.
 *  - cung IP + cung ten dang nhap: sai {@value #PER_USER} lan trong 15 phut -> khoa 15 phut
 *  - cung IP, moi ten dang nhap: sai {@value #PER_IP} lan -> khoa 15 phut (do nhieu tai khoan)
 * Tinh theo IP + ten: ke xau o may khac KHONG khoa duoc tai khoan admin that. Dung dung thi xoa dem cua cap IP + ten do.
 * Luu trong bo nho (1 may chu); khoi dong lai thi dem lai tu dau.
 */
@Component
public class LoginThrottle {
    static final int PER_USER = 5, PER_IP = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private record Count(int n, Instant since) {}
    private final Map<String, Count> fails = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottle() { this(Clock.systemUTC()); }
    LoginThrottle(Clock clock) { this.clock = clock; }

    /** IP nguoi dung: sau Caddy / nginx (X-Forwarded-For: ip that dung dau), khong co thi dia chi ket noi. */
    public static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String real = req.getHeader("X-Real-IP");
        return real != null && !real.isBlank() ? real.trim() : req.getRemoteAddr();
    }

    private static String userKey(String ip, String username) { return ip + "|" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT)); }
    private static String ipKey(String ip) { return ip + "|*"; }

    /** Goi TRUOC khi kiem mat khau: dang bi khoa thi 429 (khong kiem, khong lo mat khau dung hay sai). */
    public void check(String ip, String username) {
        Instant now = clock.instant();
        for (var e : Map.of(userKey(ip, username), PER_USER, ipKey(ip), PER_IP).entrySet()) {
            Count c = fails.get(e.getKey());
            if (c == null) continue;
            if (c.since().plus(WINDOW).isBefore(now)) { fails.remove(e.getKey(), c); continue; }
            if (c.n() >= e.getValue()) {
                long min = Math.max(1, (Duration.between(now, c.since().plus(WINDOW)).toSeconds() + 59) / 60); // lam tron len
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Nhập sai mật khẩu quá nhiều lần. Thử lại sau " + min + " phút.");
            }
        }
    }

    public void failed(String ip, String username) {
        Instant now = clock.instant();
        for (String k : new String[]{userKey(ip, username), ipKey(ip)})
            fails.merge(k, new Count(1, now), (a, b) -> a.since().plus(WINDOW).isBefore(now) ? b : new Count(a.n() + 1, a.since()));
        if (fails.size() > 10_000) fails.entrySet().removeIf(x -> x.getValue().since().plus(WINDOW).isBefore(now)); // khong phinh bo nho
    }

    public void succeeded(String ip, String username) { fails.remove(userKey(ip, username)); }
}

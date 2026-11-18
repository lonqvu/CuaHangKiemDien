package com.shop.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Token dang nhap (JWT). Phien thuong: expiration-minutes (8 gio); "Ghi nho dang nhap": remember-days (7 ngay).
 * Claim "tv" = token_version cua tai khoan luc cap: tai khoan tang version (doi mat khau, dang xuat moi thiet bi...)
 * thi token cu khong con hop le (JwtAuthFilter so sanh).
 *
 * Token "tin cay may nay" (xac thuc 2 lop): JWT rieng (typ = trust), cung khoa, gan voi ten + version tai khoan.
 */
@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;
    private final long rememberMs;
    private final long trustMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long minutes,
                      @Value("${app.jwt.remember-days:7}") long rememberDays,
                      @Value("${app.jwt.trust-device-days:7}") long trustDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = minutes * 60_000;
        this.rememberMs = rememberDays * 86_400_000L;
        this.trustMs = trustDays * 86_400_000L;
    }

    public String generate(AppUser u) { return generate(u, false); }

    public String generate(AppUser u, boolean remember) {
        Date now = new Date();
        return Jwts.builder()
                .subject(u.getUsername())
                .claim("role", u.getRole())
                .claim("name", u.getFullName())
                .claim("tv", u.getTokenVersion())
                .claim("rm", remember)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + (remember ? rememberMs : expirationMs)))
                .signWith(key)
                .compact();
    }

    /** "Khong hoi ma 2 lop tren may nay" trong trust-device-days ngay. */
    public String generateTrust(AppUser u) {
        Date now = new Date();
        return Jwts.builder()
                .subject(u.getUsername())
                .claim("typ", "trust")
                .claim("tv", u.getTokenVersion())
                .claim("ts", fingerprint(u))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + trustMs))
                .signWith(key)
                .compact();
    }

    /** Token tin cay con hieu luc cho dung tai khoan va dung version? (sai / het han / khac nguoi -> false) */
    public boolean validTrust(String token, AppUser u) {
        if (token == null || token.isBlank()) return false;
        try {
            Claims c = parse(token);
            return "trust".equals(c.get("typ", String.class)) && u.getUsername().equals(c.getSubject())
                    && versionOf(c) == u.getTokenVersion() && fingerprint(u).equals(c.get("ts", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /** Dau van tay khoa 2 lop dang dung (khong lo khoa): cai lai 2 lop = khoa moi = token tin cay cu het hieu luc. */
    static String fingerprint(AppUser u) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(u.getTotpSecret()).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(h, 0, 8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Token dang nhap co "Ghi nho" khong (de cap lai token cung kieu khi doi mat khau...). */
    public boolean remembered(String bearerToken) {
        try {
            return Boolean.TRUE.equals(parse(bearerToken).get("rm", Boolean.class));
        } catch (Exception e) {
            return false;
        }
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /** Version trong token (token cap truoc khi co V7 khong co claim -> 0). */
    public static int versionOf(Claims c) {
        Number n = c.get("tv", Number.class);
        return n == null ? 0 : n.intValue();
    }

    public static boolean isTrust(Claims c) { return "trust".equals(c.get("typ", String.class)); }
}

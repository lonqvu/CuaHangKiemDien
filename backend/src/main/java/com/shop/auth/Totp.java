package com.shop.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * Ma 6 so doi moi 30 giay (TOTP, RFC 6238, HMAC-SHA1) - giong Google Authenticator / Microsoft Authenticator / Authy.
 * Chap nhan lech +-1 buoc (30 giay) do dong ho dien thoai lech.
 */
public final class Totp {
    static final int DIGITS = 6, STEP_SECONDS = 30;
    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RNG = new SecureRandom();

    private Totp() {}

    /** Khoa moi 160 bit, dang base32 (nhap tay vao app neu khong quet duoc QR). */
    public static String newSecret() {
        byte[] b = new byte[20];
        RNG.nextBytes(b);
        return base32(b);
    }

    public static long stepAt(long epochSeconds) { return Math.floorDiv(epochSeconds, STEP_SECONDS); }

    /** Ma o buoc thoi gian step (so 6 chu so, co the bat dau bang 0). */
    public static String code(String base32Secret, long step) {
        return code(base32Decode(base32Secret), step, DIGITS);
    }

    static String code(byte[] key, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%0" + digits + "d", bin % (int) Math.pow(10, digits));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Buoc thoi gian khop voi ma (trong cua so +-1), -1 neu sai. Buoc &lt;= lastUsedStep bi tu choi (ma da dung roi).
     */
    public static long verify(String base32Secret, String code, long nowStep, Long lastUsedStep) {
        if (code == null) return -1;
        String c = code.replaceAll("\\s", "");
        if (!c.matches("\\d{" + DIGITS + "}")) return -1;
        byte[] key = base32Decode(base32Secret);
        for (long s = nowStep - 1; s <= nowStep + 1; s++) {
            if (lastUsedStep != null && s <= lastUsedStep) continue;
            if (constantEquals(code(key, s, DIGITS), c)) return s;
        }
        return -1;
    }

    /** Link de app xac thuc quet (ma QR): otpauth://totp/<cua hang>:<ten>?secret=...&issuer=... */
    public static String otpauthUri(String issuer, String account, String base32Secret) {
        String i = java.net.URLEncoder.encode(issuer, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        String a = java.net.URLEncoder.encode(account, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + i + ":" + a + "?secret=" + base32Secret + "&issuer=" + i + "&algorithm=SHA1&digits=6&period=30";
    }

    private static boolean constantEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buf = 0, bits = 0;
        for (byte d : data) {
            buf = (buf << 8) | (d & 0xff);
            bits += 8;
            while (bits >= 5) { sb.append(B32.charAt((buf >> (bits - 5)) & 31)); bits -= 5; }
        }
        if (bits > 0) sb.append(B32.charAt((buf << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] base32Decode(String s) {
        String in = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(in.length() * 5 / 8);
        int buf = 0, bits = 0;
        for (char ch : in.toCharArray()) {
            int v = B32.indexOf(ch);
            if (v < 0) throw new IllegalArgumentException("Khoa base32 khong hop le");
            buf = (buf << 5) | v;
            bits += 5;
            if (bits >= 8) { out.put((byte) ((buf >> (bits - 8)) & 0xff)); bits -= 8; }
        }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }
}

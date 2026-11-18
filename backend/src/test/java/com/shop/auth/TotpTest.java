package com.shop.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Ma 2 lop dung chuan RFC 6238 (giong Google Authenticator). */
class TotpTest {
    @Test
    void rfc6238Vectors_sha1() {
        byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        // Phu luc B RFC 6238 (8 chu so)
        assertEquals("94287082", Totp.code(key, Totp.stepAt(59), 8));
        assertEquals("07081804", Totp.code(key, Totp.stepAt(1111111109), 8));
        assertEquals("14050471", Totp.code(key, Totp.stepAt(1111111111), 8));
        assertEquals("89005924", Totp.code(key, Totp.stepAt(1234567890), 8));
        assertEquals("69279037", Totp.code(key, Totp.stepAt(2000000000), 8));
        // base32 cua khoa tren (nhu app hien)
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Totp.base32(key));
        assertEquals("287082", Totp.code("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Totp.stepAt(59)));
    }

    @Test
    void verify_windowReplayAndFormat() {
        String s = Totp.newSecret();
        assertEquals(32, s.length());
        long now = 1_000_000;
        assertEquals(now, Totp.verify(s, Totp.code(s, now), now, null));
        assertEquals(now - 1, Totp.verify(s, Totp.code(s, now - 1), now, null), "lech 30 giay van nhan");
        assertEquals(-1, Totp.verify(s, Totp.code(s, now - 2), now, null), "qua cu");
        assertEquals(-1, Totp.verify(s, Totp.code(s, now), now, now), "ma da dung khong dung lai");
        assertEquals(now, Totp.verify(s, " " + Totp.code(s, now).substring(0, 3) + " " + Totp.code(s, now).substring(3), now, null), "go co dau cach");
        assertEquals(-1, Totp.verify(s, "12345", now, null));
        assertEquals(-1, Totp.verify(s, null, now, null));
        assertTrue(Totp.otpauthUri("Nhôm Sắt Kiểm Diên", "admin", s).startsWith("otpauth://totp/Nh%C3%B4m%20S%E1%BA%AFt%20Ki%E1%BB%83m%20Di%C3%AAn:admin?secret=" + s));
    }
}

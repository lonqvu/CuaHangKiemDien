package com.shop.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Ma hoa khoa TOTP truoc khi luu DB (AES-256-GCM). Khoa = SHA-256("totp-secret|" + JWT_SECRET):
 * file sao luu / DB bi lo ma khong co JWT_SECRET (nam trong .env tren VPS) thi khong giai duoc.
 * Luu y: doi JWT_SECRET thi cac tai khoan da bat 2 lop phai cai lai (admin tat 2 lop giup o man Tai khoan).
 */
@Component
public class SecretBox {
    private static final SecureRandom RNG = new SecureRandom();
    private final SecretKeySpec key;

    public SecretBox(@Value("${app.jwt.secret}") String appSecret) {
        try {
            byte[] k = MessageDigest.getInstance("SHA-256").digest(("totp-secret|" + appSecret).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(k, "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String seal(String plain) {
        try {
            byte[] iv = new byte[12];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String open(String sealed) {
        try {
            byte[] in = Base64.getDecoder().decode(sealed);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, in, 0, 12));
            return new String(c.doFinal(in, 12, in.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Khong giai ma duoc khoa xac thuc 2 lop (JWT_SECRET da doi?)", e);
        }
    }
}

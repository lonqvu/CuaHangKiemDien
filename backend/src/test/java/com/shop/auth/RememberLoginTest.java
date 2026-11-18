package com.shop.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Ghi nho dang nhap (7 ngay / 8 gio), dang xuat moi may khac, doi mat khau / quan tri dat lai -> may khac bi dang xuat,
 * "tin cay may nay" bo qua ma 2 lop (va mat hieu luc dung luc).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RememberLoginTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    final String ip = "10.8." + (int) (Math.random() * 200) + "." + (int) (Math.random() * 200);

    private String post(String url, String json, String token) throws Exception {
        var b = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) b = b.header("Authorization", "Bearer " + token);
        var r = mvc.perform(b).andReturn().getResponse();
        assertTrue(r.getStatus() < 300, url + " -> " + r.getStatus() + " " + r.getContentAsString());
        return r.getContentAsString(StandardCharsets.UTF_8);
    }

    private int status(String token) throws Exception {
        return mvc.perform(get("/api/auth/2fa").header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    private static String field(String json, String f) {
        String v = json.replaceAll("(?s).*\"" + f + "\":(null|\"([^\"]*)\").*", "$2");
        return v.isEmpty() ? null : v;
    }

    /** Ma du phong thu i trong ket qua bat 2 lop (moi lan dang nhap can 1 ma moi: trong vai giay chi co toi da 3 ma TOTP hop le). */
    private static String recovery(String enableJson, int i) {
        return enableJson.replaceAll("(?s).*\"recoveryCodes\":\\[([^\\]]*)\\].*", "$1").replace("\"", "").split(",")[i];
    }

    private static long lifetimeSeconds(String jwt) {
        String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
        long exp = Long.parseLong(payload.replaceAll(".*\"exp\":(\\d+).*", "$1"));
        long iat = Long.parseLong(payload.replaceAll(".*\"iat\":(\\d+).*", "$1"));
        return exp - iat;
    }

    private String newUser(String role) {
        String name = "rm" + UUID.randomUUID().toString().substring(0, 6);
        AppUser u = new AppUser();
        u.setUsername(name); u.setFullName("Thu ghi nho"); u.setRole(role); u.setPasswordHash(encoder.encode("matkhau123"));
        users.save(u);
        return name;
    }

    private String login(String name, String extra) throws Exception {
        return post("/api/auth/login", "{\"username\":\"" + name + "\",\"password\":\"matkhau123\"" + extra + "}", null);
    }

    @Test
    void rememberLastsSevenDays_otherwiseEightHours() throws Exception {
        String name = newUser("STAFF");
        assertEquals(8 * 3600, lifetimeSeconds(field(login(name, ""), "token")));
        assertEquals(8 * 3600, lifetimeSeconds(field(login(name, ",\"remember\":false"), "token")));
        assertEquals(7 * 86400, lifetimeSeconds(field(login(name, ",\"remember\":true"), "token")));
    }

    @Test
    void logoutOthers_changePassword_adminReset_revokeOtherDevices() throws Exception {
        String name = newUser("STAFF");
        String phone = field(login(name, ",\"remember\":true"), "token");
        String pc = field(login(name, ""), "token");
        assertEquals(200, status(phone));

        // nhan vien cung dung duoc "dang xuat moi may khac"; may dang dung nhan token moi (giu kieu ghi nho)
        String pc2 = field(post("/api/auth/logout-others", "{}", pc), "token");
        assertEquals(401, status(phone), "dien thoai bi dang xuat");
        assertEquals(401, status(pc), "token cu cua chinh may nay cung het");
        assertEquals(200, status(pc2), "may dang dung van dung tiep");
        assertEquals(8 * 3600, lifetimeSeconds(pc2), "khong tich ghi nho -> van 8 gio");

        // doi mat khau (dang dang nhap) -> may khac ra, may dang doi nhan token moi
        String phone2 = field(login(name, ",\"remember\":true"), "token");
        String changed = post("/api/auth/change-password", "{\"oldPassword\":\"matkhau123\",\"newPassword\":\"matkhau123\"}", phone2);
        String phone3 = field(changed, "token");
        assertEquals(401, status(pc2));
        assertEquals(200, status(phone3));
        assertEquals(7 * 86400, lifetimeSeconds(phone3), "dang ghi nho -> token moi van 7 ngay");

        // quan tri dat lai mat khau -> moi may cua nguoi do ra
        String admin = field(post("/api/auth/login", "{\"username\":\"admin\",\"password\":\"admin123\"}", null), "token");
        long id = users.findByUsername(name).orElseThrow().getId();
        post("/api/users/" + id + "/reset-password", "{\"password\":\"matkhau123\"}", admin);
        assertEquals(401, status(phone3));
    }

    @Test
    void trustDevice_skipsCodeForSevenDays_andIsRevokedCorrectly() throws Exception {
        String name = newUser("ADMIN");
        String t = field(login(name, ""), "token");
        String secret = field(post("/api/auth/2fa/setup", "{\"password\":\"matkhau123\"}", t), "secret");
        long now = Totp.stepAt(Instant.now().getEpochSecond());
        String en = post("/api/auth/2fa/enable", "{\"code\":\"" + Totp.code(secret, now) + "\"}", t);

        // nhap ma + tick "tin cay may nay" -> nhan trustToken (7 ngay)
        String r1 = login(name, ",\"otp\":\"" + Totp.code(secret, now + 1) + "\",\"trustDevice\":true");
        String trust = field(r1, "trustToken");
        assertNotNull(trust);
        assertEquals(7 * 86400, lifetimeSeconds(trust));
        // khong tick -> khong cap
        assertNull(field(login(name, ",\"otp\":\"" + recovery(en, 0) + "\""), "trustToken"));

        // lan sau: may da tin cay -> vao ngay, khong hoi ma
        String r2 = login(name, ",\"trustToken\":\"" + trust + "\"");
        assertTrue(r2.contains("\"otpRequired\":false") && field(r2, "token") != null, r2);
        // trust token khong dung lam token dang nhap
        assertEquals(401, status(trust));
        // may khac (khong co trust token) van bi hoi ma
        assertTrue(login(name, "").contains("\"otpRequired\":true"));
        // trust token cua nguoi khac khong dung duoc
        String other = newUser("ADMIN");
        assertTrue(login(other, ",\"trustToken\":\"" + trust + "\"").contains("\"otpRequired\":false"), "nguoi khac chua bat 2 lop: vao binh thuong");

        // dang xuat moi may khac -> trust cu het hieu luc, phai nhap ma lai
        post("/api/auth/logout-others", "{}", field(r2, "token"));
        assertTrue(login(name, ",\"trustToken\":\"" + trust + "\"").contains("\"otpRequired\":true"));
    }

    @Test
    void trustDevice_revokedWhenTwoFactorReinstalled() throws Exception {
        String name = newUser("ADMIN");
        String t = field(login(name, ""), "token");
        String s1 = field(post("/api/auth/2fa/setup", "{\"password\":\"matkhau123\"}", t), "secret");
        long now = Totp.stepAt(Instant.now().getEpochSecond());
        String en = post("/api/auth/2fa/enable", "{\"code\":\"" + Totp.code(s1, now) + "\"}", t);
        String trust = field(login(name, ",\"otp\":\"" + Totp.code(s1, now + 1) + "\",\"trustDevice\":true"), "trustToken");
        // tat roi cai lai voi dien thoai moi (khoa moi)
        post("/api/auth/2fa/disable", "{\"password\":\"matkhau123\",\"code\":\"" + recovery(en, 0) + "\"}", t);
        String s2 = field(post("/api/auth/2fa/setup", "{\"password\":\"matkhau123\"}", t), "secret");
        post("/api/auth/2fa/enable", "{\"code\":\"" + Totp.code(s2, now) + "\"}", t);
        assertTrue(login(name, ",\"trustToken\":\"" + trust + "\"").contains("\"otpRequired\":true"), "may tin cay cu phai nhap ma lai");
    }
}

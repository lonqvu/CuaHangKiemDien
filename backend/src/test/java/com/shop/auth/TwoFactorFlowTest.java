package com.shop.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Xac thuc 2 lop qua API that: bat -> dang nhap can ma -> ma sai / dung lai bi tu choi -> ma du phong 1 lan -> tat. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TwoFactorFlowTest {
    @Autowired MockMvc mvc;
    @Autowired SecretBox box;
    @Autowired AppUserRepository users;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    final String ip = "10.9." + (int) (Math.random() * 200) + "." + (int) (Math.random() * 200); // tach dem dang nhap sai khoi test khac

    private ResultActions postJson(String url, String json, String token) throws Exception {
        var b = post(url).header("X-Forwarded-For", ip).contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) b = b.header("Authorization", "Bearer " + token);
        return mvc.perform(b);
    }

    private static String field(String json, String f) { return json.replaceAll("(?s).*\"" + f + "\":\"([^\"]*)\".*", "$1"); }

    private String login(String user, String pass, String otp) throws Exception {
        return postJson("/api/auth/login", "{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"" + (otp == null ? "" : ",\"otp\":\"" + otp + "\"") + "}", null)
                .andReturn().getResponse().getContentAsString();
    }

    private long now() { return Totp.stepAt(Instant.now().getEpochSecond()); }

    @Test
    void fullFlow() throws Exception {
        String name = "tf" + UUID.randomUUID().toString().substring(0, 6);
        AppUser u = new AppUser();
        u.setUsername(name); u.setFullName("Thu 2 lop"); u.setRole("ADMIN"); u.setPasswordHash(encoder.encode("matkhau123"));
        users.save(u);
        String token = field(login(name, "matkhau123", null), "token");

        // cai dat: sai mat khau -> tu choi; dung -> QR + khoa
        postJson("/api/auth/2fa/setup", "{\"password\":\"sai\"}", token).andExpect(status().isBadRequest());
        String setup = postJson("/api/auth/2fa/setup", "{\"password\":\"matkhau123\"}", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/"))).andReturn().getResponse().getContentAsString();
        String secret = field(setup, "secret");
        assertNotEquals(secret, users.findByUsername(name).orElseThrow().getTotpSecret(), "khoa luu DB phai duoc ma hoa");
        assertEquals(secret, box.open(users.findByUsername(name).orElseThrow().getTotpSecret()));

        // bat: ma sai -> 400; dung -> 8 ma du phong
        postJson("/api/auth/2fa/enable", "{\"code\":\"000000\"}", token).andExpect(status().isBadRequest());
        String enableCode = Totp.code(secret, now());
        String en = postJson("/api/auth/2fa/enable", "{\"code\":\"" + enableCode + "\"}", token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.recoveryCodes.length()").value(8)).andReturn().getResponse().getContentAsString();
        String recovery = en.replaceAll("(?s).*\"recoveryCodes\":\\[\"([^\"]+)\".*", "$1");
        mvc.perform(get("/api/auth/2fa").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.recoveryLeft").value(8));

        // dang nhap: dung mat khau -> otpRequired, chua co token
        String step1 = login(name, "matkhau123", null);
        assertTrue(step1.contains("\"otpRequired\":true") && step1.contains("\"token\":null"), step1);
        // ma vua dung de bat -> khong dung lai duoc
        postJson("/api/auth/login", "{\"username\":\"" + name + "\",\"password\":\"matkhau123\",\"otp\":\"" + enableCode + "\"}", null).andExpect(status().isUnauthorized());
        // ma buoc ke tiep (cua so +-30 giay) -> vao duoc
        String ok = login(name, "matkhau123", Totp.code(secret, now() + 1));
        assertTrue(ok.contains("\"twoFactor\":true") && !field(ok, "token").isBlank(), ok);
        // sai mat khau van 401, khong lo co bat 2 lop hay khong
        postJson("/api/auth/login", "{\"username\":\"" + name + "\",\"password\":\"sai\"}", null).andExpect(status().isUnauthorized());

        // ma du phong: dung 1 lan
        assertFalse(field(login(name, "matkhau123", recovery.toUpperCase()), "token").isBlank(), "ma du phong (go chu hoa) vao duoc");
        postJson("/api/auth/login", "{\"username\":\"" + name + "\",\"password\":\"matkhau123\",\"otp\":\"" + recovery + "\"}", null).andExpect(status().isUnauthorized());

        // doi mat khau tu man dang nhap (chua dang nhap): can ma
        postJson("/api/auth/change-password", "{\"username\":\"" + name + "\",\"oldPassword\":\"matkhau123\",\"newPassword\":\"moi123456\"}", null).andExpect(status().isBadRequest());

        // quan tri khac tat giup
        String admin = field(login("admin", "admin123", null), "token");
        postJson("/api/users/" + users.findByUsername(name).orElseThrow().getId() + "/2fa/reset", "{}", admin).andExpect(status().isOk()).andExpect(jsonPath("$.twoFactor").value(false));
        assertTrue(login(name, "matkhau123", null).contains("\"otpRequired\":false"));
    }

    @Test
    void staffCannotResetOthers_andSelfDisableNeedsPasswordAndCode() throws Exception {
        String name = "tg" + UUID.randomUUID().toString().substring(0, 6);
        AppUser u = new AppUser();
        u.setUsername(name); u.setFullName("NV"); u.setRole("STAFF"); u.setPasswordHash(encoder.encode("matkhau123"));
        users.save(u);
        String token = field(login(name, "matkhau123", null), "token");
        postJson("/api/users/1/2fa/reset", "{}", token).andExpect(status().isForbidden());
        String secret = field(postJson("/api/auth/2fa/setup", "{\"password\":\"matkhau123\"}", token).andReturn().getResponse().getContentAsString(), "secret");
        postJson("/api/auth/2fa/enable", "{\"code\":\"" + Totp.code(secret, now()) + "\"}", token).andExpect(status().isOk());
        postJson("/api/auth/2fa/disable", "{\"password\":\"matkhau123\",\"code\":\"000000\"}", token).andExpect(status().isBadRequest());
        postJson("/api/auth/2fa/disable", "{\"password\":\"sai\",\"code\":\"" + Totp.code(secret, now() + 1) + "\"}", token).andExpect(status().isBadRequest());
        postJson("/api/auth/2fa/disable", "{\"password\":\"matkhau123\",\"code\":\"" + Totp.code(secret, now() + 1) + "\"}", token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
    }
}

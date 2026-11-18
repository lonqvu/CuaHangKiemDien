package com.shop.setting;

import com.shop.auth.AppUser;
import com.shop.auth.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Anh nen man dang nhap: ai cung xem (chua dang nhap), chi quan tri dat / xoa, chi nhan anh that, toi da 3 MB. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginImageTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    private String token(String user, String pass) throws Exception {
        return mvc.perform(post("/api/auth/login").header("X-Forwarded-For", "10.7.0." + (int) (Math.random() * 250))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void publicGet_adminPutDelete_validation() throws Exception {
        String admin = token("admin", "admin123");
        mvc.perform(delete("/api/settings/login-image").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(get("/api/public/login-image")).andExpect(status().isNoContent()); // chua dat -> giao dien mac dinh

        // dat anh PNG -> ai cung xem duoc, dung loai, co ETag; hoi lai voi ETag -> 304
        mvc.perform(put("/api/settings/login-image").header("Authorization", "Bearer " + admin).contentType("image/png").content(PNG))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasImage").value(true)).andExpect(jsonPath("$.updatedBy").value("admin"));
        var r = mvc.perform(get("/api/public/login-image")).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control", "no-cache")).andReturn().getResponse();
        assertArrayEquals(PNG, r.getContentAsByteArray());
        String etag = r.getHeader("ETag");
        assertNotNull(etag);
        mvc.perform(get("/api/public/login-image").header("If-None-Match", etag)).andExpect(status().isNotModified());

        // loai anh xet theo noi dung, khong tin Content-Type gui len
        mvc.perform(put("/api/settings/login-image").header("Authorization", "Bearer " + admin).contentType("image/png").content(JPG))
                .andExpect(status().isOk());
        mvc.perform(get("/api/public/login-image")).andExpect(content().contentType("image/jpeg"));
        mvc.perform(put("/api/settings/login-image").header("Authorization", "Bearer " + admin).contentType("image/png").content("<svg onload=alert(1)>".getBytes()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Chỉ nhận ảnh JPG, PNG hoặc WebP"));
        byte[] big = new byte[LoginImageController.MAX_BYTES + 1];
        System.arraycopy(JPG, 0, big, 0, JPG.length);
        mvc.perform(put("/api/settings/login-image").header("Authorization", "Bearer " + admin).contentType("image/jpeg").content(big))
                .andExpect(status().isBadRequest());

        // nhan vien / chua dang nhap khong doi duoc
        AppUser nv = new AppUser();
        nv.setUsername("li" + UUID.randomUUID().toString().substring(0, 6)); nv.setFullName("NV"); nv.setRole("STAFF");
        nv.setPasswordHash(encoder.encode("matkhau123"));
        users.save(nv);
        String staff = token(nv.getUsername(), "matkhau123");
        mvc.perform(put("/api/settings/login-image").header("Authorization", "Bearer " + staff).contentType("image/png").content(PNG)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/settings/login-image").header("Authorization", "Bearer " + staff)).andExpect(status().isForbidden());
        mvc.perform(put("/api/settings/login-image").contentType("image/png").content(PNG)).andExpect(status().isUnauthorized());

        // quan tri xoa -> ve mac dinh
        mvc.perform(delete("/api/settings/login-image").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(get("/api/public/login-image")).andExpect(status().isNoContent());
    }
}

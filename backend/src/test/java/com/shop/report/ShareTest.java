package com.shop.report;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Gui Zalo tu may tinh: trang web gui PDF len -> ma ngau nhien -> tien ich tren may tai ve (khong can dang nhap). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShareTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    static final byte[] PDF = "%PDF-1.4 test".getBytes();

    private String token(String u, String p) throws Exception {
        return mvc.perform(post("/api/auth/login").header("X-Forwarded-For", "10.6.0." + (int) (Math.random() * 250))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void nhanVienGuiDuoc_taiVeKhongCanDangNhap_tenFileAnToan() throws Exception {
        if (users.findByUsername("nv_share").isEmpty()) {
            AppUser u = new AppUser();
            u.setUsername("nv_share"); u.setPasswordHash(encoder.encode("nv123456")); u.setFullName("NV"); u.setRole("STAFF");
            users.save(u);
        }
        String staff = token("nv_share", "nv123456");
        mvc.perform(post("/api/share?name=x.pdf").content(PDF)).andExpect(status().isUnauthorized());
        String res = mvc.perform(post("/api/share").param("name", "Công nợ chú Đông & \"x\".pdf").header("Authorization", "Bearer " + staff).content(PDF))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String t = res.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        String name = res.replaceAll(".*\"name\":\"([^\"]+)\".*", "$1");
        assertTrue(t.matches("[A-Za-z0-9_-]{43}"), t);
        assertTrue(name.matches("[A-Za-z0-9_.-]+\\.pdf"), name);
        assertEquals("Cong_no_chu_Dong_x_.pdf", name);
        byte[] got = mvc.perform(get("/api/public/share/" + t)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(PDF, got);
        mvc.perform(get("/api/public/share/khong-co-ma-nay-xxxxxxxxxxxxxxxxxxxxxxxx")).andExpect(status().isNotFound());
        // chi nhan PDF / PNG
        mvc.perform(post("/api/share?name=a.exe").header("Authorization", "Bearer " + staff).content("MZ...".getBytes()))
                .andExpect(status().isBadRequest());
    }
}

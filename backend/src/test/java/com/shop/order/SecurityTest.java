package com.shop.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityTest {
    @Autowired MockMvc mvc;

    private String loginToken() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void noToken_is401() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPassword_is401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"sai\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminToken_canCallApi() throws Exception {
        String t = loginToken();
        mvc.perform(get("/api/products").header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        mvc.perform(get("/api/reports/revenue?from=2026-01-01&to=2026-12-31").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reports/overview").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andExpect(jsonPath("$.month.current").exists());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].username").value("admin"));
    }

    private String token(String user, String pass) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private long createStaff(String admin, String username) throws Exception {
        String body = mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"fullName\":\"Nhân viên\",\"role\":\"STAFF\",\"password\":\"nv123456\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    /** Nhan vien chi ban hang: so no / sao ke / danh sach hoa don / bao cao la cua quan tri (403, khong phai 401). */
    @Test
    void staff_onlySale() throws Exception {
        String admin = loginToken();
        createStaff(admin, "nv_sec1");
        String nv = "Bearer " + token("nv_sec1", "nv123456");
        String body = mvc.perform(post("/api/customers").header("Authorization", nv).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Khach test quyen\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");
        for (String ok : new String[]{"/api/products", "/api/customers?q=", "/api/customers/" + id})
            mvc.perform(get(ok).header("Authorization", nv)).andExpect(status().isOk());
        for (String no : new String[]{"/api/orders?status=PAID", "/api/customers/" + id + "/debt-ledger", "/api/customers/" + id + "/statement",
                "/api/customers/" + id + "/statement.pdf", "/api/reports/overview", "/api/users"})
            mvc.perform(get(no).header("Authorization", nv)).andExpect(status().isForbidden());
    }

    /** Khoa tai khoan / doi vai tro co hieu luc ngay voi token dang dung. */
    @Test
    void lockedAccount_oldTokenStopsWorking() throws Exception {
        String admin = loginToken();
        long id = createStaff(admin, "nv_sec2");
        String nv = "Bearer " + token("nv_sec2", "nv123456");
        mvc.perform(get("/api/products").header("Authorization", nv)).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/" + id)
                .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Nhân viên\",\"role\":\"STAFF\",\"active\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/products").header("Authorization", nv)).andExpect(status().isUnauthorized());
    }

    /** Goi sai phuong thuc / sai kieu du lieu -> 405 / 415 (loi phia goi), khong phai 500 "may chu gap loi". */
    @Test
    void wrongMethodOrMediaType_is4xxNot500() throws Exception {
        String t = loginToken();
        mvc.perform(get("/api/products/1").header("Authorization", "Bearer " + t))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.message").exists());
        mvc.perform(post("/api/categories").header("Authorization", "Bearer " + t).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }
}

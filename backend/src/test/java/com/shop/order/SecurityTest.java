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
}

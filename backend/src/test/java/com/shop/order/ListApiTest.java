package com.shop.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API danh sach co phan trang + xu ly loi (khong de loi server bien thanh 401 lam dang xuat nguoi dung). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListApiTest {
    @Autowired MockMvc mvc;
    String auth;

    @BeforeEach
    void login() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        auth = "Bearer " + body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private String json(String method, String url, String body) throws Exception {
        var req = switch (method) { case "POST" -> post(url); case "PUT" -> put(url); default -> get(url); };
        return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static long id(String json) { return Long.parseLong(json.replaceAll("^\\{\"id\":(\\d+).*", "$1")); }

    @Test
    void orders_arePagedWithSummary_andListHasNoItems() throws Exception {
        long productId = id(json("POST", "/api/products", "{\"name\":\"Thep hop test\",\"unit\":\"cay\",\"price\":1000}"));
        for (int i = 0; i < 3; i++) {
            long orderId = id(json("POST", "/api/orders", null));
            json("POST", "/api/orders/" + orderId + "/items", "{\"productId\":" + productId + ",\"quantity\":2}");
            json("POST", "/api/orders/" + orderId + "/checkout", "{\"customerName\":\"Khach le\",\"paidAmount\":2000}");
        }
        mvc.perform(get("/api/orders?status=PAID&size=2&sort=itemsTotal,desc").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].items").doesNotExist())
                .andExpect(jsonPath("$.items[0].code", startsWith("HD")))
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.summary.countByStatus.PAID", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.summary.itemsTotal", greaterThanOrEqualTo(6000.0)));
        // tim theo ten khach, trang 2
        mvc.perform(get("/api/orders").param("status", "PAID").param("q", "khach le").param("size", "1").param("page", "1")
                        .header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void customers_paged_withDebtFilterAndTotal() throws Exception {
        json("POST", "/api/customers", "{\"name\":\"Khach no test\",\"initialDebt\":500000}");
        json("POST", "/api/customers", "{\"name\":\"Khach khong no test\"}");
        mvc.perform(get("/api/customers?debt=true&q=test").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", hasItem("Khach no test")))
                .andExpect(jsonPath("$.items[*].name", not(hasItem("Khach khong no test"))))
                .andExpect(jsonPath("$.summary.totalDebt", greaterThanOrEqualTo(500000.0)));
    }

    @Test
    void products_discontinuedCanBeListedAndRestored() throws Exception {
        long id = id(json("POST", "/api/products", "{\"name\":\"Ton ngung ban test\",\"unit\":\"m\",\"price\":1}"));
        mvc.perform(delete("/api/products/" + id).header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/products?active=false&q=ngung ban test").header("Authorization", auth))
                .andExpect(jsonPath("$.items[0].id").value(id));
        json("POST", "/api/products/" + id + "/restore", null);
        mvc.perform(get("/api/products?q=ngung ban test").header("Authorization", auth))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void unknownPath_is404NotLogout() throws Exception {
        mvc.perform(get("/api/khong-co-duong-dan-nay").header("Authorization", auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    void validationMessage_isVietnamese() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"unit\":\"m\",\"price\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Tên: chưa nhập"));
    }
}

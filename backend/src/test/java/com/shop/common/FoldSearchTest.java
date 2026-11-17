package com.shop.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tim khong dau o cac danh sach phan trang trong SQL (Fold: translate() bo dau tieng Viet, chay tren H2 giong Postgres):
 * hoa don ("tuan" -> "Anh Tuấn"), ton kho ("ton lanh" -> "Tôn lạnh"), phieu nhap (ten NCC, ghi chu), kiem ke (ghi chu).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoldSearchTest {
    @Autowired MockMvc mvc;
    String token;

    private String call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String json) throws Exception {
        if (json != null) b = b.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(b.header("Authorization", "Bearer " + token)).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
    }

    private long id(String json) { return Long.parseLong(json.replaceAll("(?s)^\\{\"id\":(\\d+).*", "$1")); }

    private int total(String json) { return Integer.parseInt(json.replaceAll("(?s).*\"total\":(\\d+),\"page\".*", "$1")); } // tong cua trang (dong co the co truong total khac)

    @Test
    void listsSearchWithoutVietnameseMarks() throws Exception {
        token = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        long p = id(call(post("/api/products"), "{\"name\":\"FS Tôn lạnh màu đỏ 0,4 ly\",\"unit\":\"m\",\"price\":95000}"));
        long c = id(call(post("/api/customers"), "{\"name\":\"FS Anh Tuấn thợ hàn\",\"phone\":\"0905000111\"}"));
        long o = id(call(post("/api/orders"), null));
        call(post("/api/orders/" + o + "/items"), "{\"productId\":" + p + ",\"quantity\":2,\"sheets\":0,\"factor\":0,\"unitPrice\":95000}");
        call(post("/api/orders/" + o + "/checkout"), "{\"customerId\":" + c + ",\"paidAmount\":190000,\"note\":\"Giao tận nhà\"}");

        assertEquals(1, total(call(get("/api/orders").param("q", "fs anh tuan tho han"), null)), "hoa don: ten khach khong dau");
        assertEquals(1, total(call(get("/api/orders").param("q", "giao tan nha"), null)), "hoa don: ghi chu khong dau");
        assertEquals(1, total(call(get("/api/orders").param("q", "FS ANH TUẤN"), null)), "hoa don: co dau, chu hoa van thay");

        long s = id(call(post("/api/suppliers"), "{\"name\":\"FS Đại lý tôn Hoa Sen\"}"));
        call(post("/api/receipts"), "{\"supplierId\":" + s + ",\"note\":\"FS Hàng về chậm\",\"paidAmount\":0,\"lines\":[{\"productId\":" + p
                + ",\"inputUnit\":\"m\",\"inputQty\":10,\"inputPrice\":70000}]}");
        assertEquals(1, total(call(get("/api/receipts").param("status", "DRAFT").param("q", "fs dai ly ton"), null)), "phieu nhap: ten NCC");
        assertEquals(1, total(call(get("/api/receipts").param("status", "DRAFT").param("q", "fs hang ve cham"), null)), "phieu nhap: ghi chu");

        assertEquals(1, total(call(get("/api/stock").param("q", "fs ton lanh mau do"), null)), "ton kho: ten san pham");

        call(post("/api/stock-counts"), "{\"note\":\"FS Kiểm kê cuối tháng\"}");
        assertEquals(1, total(call(get("/api/stock-counts").param("status", "DRAFT").param("q", "fs kiem ke cuoi"), null)), "kiem ke: ghi chu");

        // ky tu dac biet cua LIKE nguoi dung go khong bien thanh ky tu dai dien
        assertEquals(0, total(call(get("/api/orders").param("q", "fs%zz"), null)));
    }
}

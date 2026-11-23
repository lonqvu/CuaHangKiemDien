package com.shop.legacy;

import com.shop.auth.AppUser;
import com.shop.auth.AppUserRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Nhap du lieu phan mem cu tu man web. Database rieng (trong) cho bo test nay.
 * Du lieu mau gom du cac truong hop that: loai trung ten, khach le "con no", khach le co no cu, khach tra trong,
 * dong cua hoa don da xoa, san pham da xoa, so tien bat thuong.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:legacy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LegacyImportTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired LegacyVerifier verifier;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper om;

    static final String DATA = """
        {"nguon":"NhomSatKiemDien (SQL Server)","xuatLuc":"2026-11-19T01:30:15Z","invoiceCounter":120,
         "classify":[{"id":1,"name":"Tôn"},{"id":2,"name":"Bánh xe "},{"id":3,"name":"bánh  xe"},{"id":4,"name":"Hộp"}],
         "products":[
           {"id":1,"classifyId":1,"name":"Tôn  hoa sen 0.4","price":"98000.0","unit":"m2","createdAt":"2024-07-01 08:00:00.000"},
           {"id":5,"classifyId":3,"name":"Bánh xe tủ","price":"25000","unit":"cái ","createdAt":null},
           {"id":9,"classifyId":4,"name":"Hộp 30x60 1.4","price":"220000.0","unit":"Cây","createdAt":"2024-07-02 09:00:00.000"}],
         "customers":[
           {"id":1030,"name":"chú hùng","address":"Yên Mô","phone":"0912345678","debt":"349862778.0000","createdAt":"2024-07-01 08:00:00.000"},
           {"id":1044,"name":"Chú hải","address":null,"phone":null,"debt":"149702500.0000","createdAt":null},
           {"id":2000,"name":"Khách hết nợ","address":null,"phone":"","debt":".0000","createdAt":null}],
         "orders":[
           {"id":"HD00000007","customer":"Anh Lương","address":null,"phone":null,"date":"2024-07-28 09:36:36.173","customerId":null,
            "totalBill":"21594312.0000","oldDebt":null,"totalOrder":"21594312.0000","pay":"21594312.0000","debtBack":".0000","ship":null,"staff":"Kiểm Diên","note":null},
           {"id":"HD00000064","customer":"chú hùng","address":null,"phone":null,"date":"2024-08-01 10:00:00.000","customerId":1030,
            "totalBill":"9271520.0000","oldDebt":"79998000.0000","totalOrder":"89269520.0000","pay":null,"debtBack":"89029520.0000","ship":null,"staff":"Kiểm Diên","note":null},
           {"id":"HD00000081","customer":"Chú hồng","address":null,"phone":null,"date":"2024-08-05 10:00:00.000","customerId":null,
            "totalBill":"8198178.0000","oldDebt":"22593000.0000","totalOrder":"30791178.0000","pay":".0000","debtBack":"30791178.0000","ship":".0000","staff":"Kiểm Diên","note":"giao chiều"},
           {"id":"HD00000100","customer":"m","address":null,"phone":null,"date":"2026-11-18 16:50:48.750","customerId":null,
            "totalBill":"1400000.0000","oldDebt":".0000","totalOrder":"1400000.0000","pay":".0000","debtBack":"1400000.0000","ship":".0000","staff":"Kiểm Diên","note":null},
           {"id":"HD00000110","customer":"Chú hải","address":null,"phone":null,"date":"2026-05-14 09:00:00.000","customerId":1044,
            "totalBill":"1354880.0000","oldDebt":"39019424.0000","totalOrder":"40374304.0000","pay":"8000000000.0000","debtBack":"32374304.0000","ship":".0000","staff":"Kiểm Diên","note":null}],
         "bills":[
           {"id":1,"orderId":"HD00000007","productId":1,"productName":"Tôn  hoa sen 0.4","unit":"m2","amount":"3.490","price":"165000.0000","intoMoney":"3731508.0000","heSo":"1.080","soTam":6,"tongSoLuong":"22.615"},
           {"id":2,"orderId":"HD00000007","productId":777,"productName":"Hàng đã xóa","unit":"cây","amount":"20.000","price":"155000.0000","intoMoney":"3100000.0000","heSo":".000","soTam":0,"tongSoLuong":"20.000"},
           {"id":3,"orderId":"HD00000064","productId":9,"productName":"Hộp 30x60 1.4","unit":"Cây","amount":"2.000","price":"220000.0000","intoMoney":"440000.0000","heSo":".000","soTam":0,"tongSoLuong":"2.000"},
           {"id":4,"orderId":"HD99999999","productId":9,"productName":"Dòng của hóa đơn đã xóa","unit":"Cây","amount":"1.000","price":"220000.0000","intoMoney":"220000.0000","heSo":".000","soTam":0,"tongSoLuong":"1.000"},
           {"id":5,"orderId":"HD00000100","productId":5,"productName":"Bánh xe tủ","unit":"cái","amount":"56.000","price":"25000.0000","intoMoney":"1400000.0000","heSo":".000","soTam":0,"tongSoLuong":"56.000"}],
         "payments":[{"id":1,"customerId":1030,"amount":"50000000.0000","date":"2026-11-16 07:56:56.233","deleted":false}]}
        """;

    private String token(String user, String pass) throws Exception {
        return mvc.perform(post("/api/auth/login").header("X-Forwarded-For", "10.9.0." + (int) (Math.random() * 250))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private static byte[] gzip(String s) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(out)) { gz.write(s.getBytes(StandardCharsets.UTF_8)); }
        return out.toByteArray();
    }

    @Autowired com.shop.auth.TwoFactorService twoFactorService;
    /** ma du phong cua quan tri 'qt_legacy' (moi ma dung 1 lan): [0] dang nhap, [1].. xoa du lieu */
    static java.util.List<String> rc;
    static String qtToken;

    /** Quan tri da bat xac thuc 2 lop (tao 1 lan). */
    private String twoFactorAdmin() throws Exception {
        if (qtToken != null) return qtToken;
        AppUser u = new AppUser();
        u.setUsername("qt_legacy"); u.setPasswordHash(encoder.encode("qt123456")); u.setFullName("QT"); u.setRole("ADMIN");
        users.save(u);
        var setup = twoFactorService.setup(u);
        rc = twoFactorService.enable(u, com.shop.auth.Totp.code(setup.secret(), com.shop.auth.Totp.stepAt(java.time.Instant.now().getEpochSecond())));
        qtToken = mvc.perform(post("/api/auth/login").header("X-Forwarded-For", "10.9.1.1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"qt_legacy\",\"password\":\"qt123456\",\"otp\":\"" + rc.get(0) + "\"}"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        return qtToken;
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token, String password, String code) throws Exception {
        return mvc.perform(post("/api/admin/legacy-import/reset").header("Authorization", "Bearer " + token)
                .header("X-Forwarded-For", "10.9.2." + (int) (Math.random() * 250)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\",\"code\":\"" + code + "\"}"));
    }

    private int count(String t) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Integer.class); }

    @Test
    @Order(0)
    void xoaDuLieuThu_truocKhiNhap() throws Exception {
        String admin = token("admin", "admin123");
        // du lieu thu: 1 loai, 1 san pham, 1 khach co no, 1 hoa don ban chiu
        mvc.perform(post("/api/categories").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Thử\"}")).andExpect(status().isOk());
        jdbc.update("INSERT INTO product (name, unit, price) VALUES ('SP thử', 'cái', 1000)");
        String cust = mvc.perform(post("/api/customers").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Khách thử\",\"initialDebt\":50000}")).andReturn().getResponse().getContentAsString().replaceAll("^\\{\"id\":(\\d+).*", "$1");
        Long pid = jdbc.queryForObject("SELECT id FROM product WHERE name = 'SP thử'", Long.class);
        String draft = mvc.perform(post("/api/orders").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString().replaceAll("^\\{\"id\":(\\d+).*", "$1");
        mvc.perform(post("/api/orders/" + draft + "/items").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":" + pid + ",\"quantity\":2}")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + draft + "/checkout").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":" + cust + ",\"paidAmount\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("HD00000001"));

        mvc.perform(get("/api/admin/legacy-import").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.canImport").value(false)).andExpect(jsonPath("$.canReset").value(true))
                .andExpect(jsonPath("$.blockedReason").value(org.hamcrest.Matchers.containsString("1 khách")));
        // quan tri chua bat 2 lop -> khong xoa duoc
        reset(admin, "admin123", "123456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("xác thực 2 lớp")));
        // quan tri da bat 2 lop: sai mat khau / sai ma -> khong xoa
        String qt = twoFactorAdmin();
        reset(qt, "sai-mat-khau", rc.get(1)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mật khẩu không đúng"));
        reset(qt, "qt123456", "000000").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mã xác thực không đúng"));
        assertEquals(1, count("customer"));

        reset(qt, "qt123456", rc.get(1)).andExpect(status().isOk()).andExpect(jsonPath("$.deleted.orders").value(1));
        for (String t : LegacyImportService.MUST_BE_EMPTY) assertEquals(0, count(t), t + " phai trong sau khi xoa");
        assertEquals(0, count("stock_balance"));
        assertTrue(count("app_user") >= 1, "giu tai khoan");
        mvc.perform(get("/api/admin/legacy-import").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.canImport").value(true)).andExpect(jsonPath("$.canReset").value(false));
    }

    @Test
    @Order(1)
    void quyen_vaFileSai() throws Exception {
        if (users.findByUsername("nv_legacy").isEmpty()) {
            AppUser u = new AppUser();
            u.setUsername("nv_legacy"); u.setPasswordHash(encoder.encode("nv123456")); u.setFullName("NV"); u.setRole("STAFF");
            users.save(u);
        }
        String admin = token("admin", "admin123"), staff = token("nv_legacy", "nv123456");
        mvc.perform(get("/api/admin/legacy-import")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/legacy-import").header("Authorization", "Bearer " + staff)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/legacy-import").header("Authorization", "Bearer " + staff).content(DATA)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/legacy-import/reset").header("Authorization", "Bearer " + staff).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"nv123456\",\"code\":\"123456\"}")).andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/legacy-import").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canImport").value(true));
        mvc.perform(post("/api/admin/legacy-import").header("Authorization", "Bearer " + admin).content("khong phai json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("du-lieu-cu.json")));
        mvc.perform(post("/api/admin/legacy-import").header("Authorization", "Bearer " + admin).content("{\"orders\":[]}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, count("customer"));
    }

    @Test
    @Order(2)
    void kiemTraThu_khongGhiGi() throws Exception {
        String admin = token("admin", "admin123");
        mvc.perform(post("/api/admin/legacy-import?dryRun=true").header("Authorization", "Bearer " + admin).content(gzip(DATA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.committed").value(false))
                .andExpect(jsonPath("$.report.errorCount").value(0))
                .andExpect(jsonPath("$.stats.orders").value(5))
                .andExpect(jsonPath("$.stats.droppedItems").value(1))
                .andExpect(jsonPath("$.report.anomalies").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("HD00000110"))));
        for (String t : LegacyImportService.MUST_BE_EMPTY) assertEquals(0, count(t), t + " phai con trong sau kiem tra thu");
        assertEquals(0, count("legacy_import_log"));
    }

    @Test
    @Order(3)
    void nhapThat_dungQuyTac() throws Exception {
        String admin = token("admin", "admin123");
        mvc.perform(post("/api/admin/legacy-import?dryRun=false").header("Authorization", "Bearer " + admin).content(DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.committed").value(true));

        assertEquals(3, count("category"), "gop 'Bánh xe ' va 'bánh  xe'");
        assertEquals(3, count("product"));
        assertEquals(3, count("stock_balance"));
        assertEquals(5, count("orders"));
        assertEquals(4, count("order_item"), "bo dong cua hoa don da xoa");
        assertEquals(1, count("legacy_import_log"));

        // no = so no cu, 1 dong so du; khach het no khong co dong so no
        assertEquals(0, new BigDecimal("349862778").compareTo(jdbc.queryForObject("SELECT debt FROM customer WHERE id = 1030", BigDecimal.class)));
        assertEquals(2, count("debt_ledger"));
        assertEquals("Số dư chuyển từ phần mềm cũ", jdbc.queryForObject("SELECT note FROM debt_ledger WHERE customer_id = 1030", String.class));
        mvc.perform(get("/api/customers/1030/debt-ledger").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.items[0].description").value(org.hamcrest.Matchers.startsWith("Nợ chuyển từ phần mềm cũ (đến ")));

        // khach le "con no" -> tra du; khach le co no cu -> bo khoi tong, ghi chu
        var m = jdbc.queryForMap("SELECT paid_amount, debt_after, previous_debt, total_payable, note FROM orders WHERE code = 'HD00000100'");
        assertEquals(0, new BigDecimal("1400000").compareTo((BigDecimal) m.get("paid_amount")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) m.get("debt_after")));
        var h = jdbc.queryForMap("SELECT previous_debt, total_payable, paid_amount, note FROM orders WHERE code = 'HD00000081'");
        assertEquals(0, new BigDecimal("8198178").compareTo((BigDecimal) h.get("total_payable")));
        assertEquals("giao chiều. Phần mềm cũ ghi nợ cũ 22593000 đ", h.get("note"));
        // khach quen tra trong -> tong - con no; so bat thuong (8 ty) de nguyen
        assertEquals(0, new BigDecimal("240000").compareTo(jdbc.queryForObject("SELECT paid_amount FROM orders WHERE code = 'HD00000064'", BigDecimal.class)));
        assertEquals(0, new BigDecimal("8000000000").compareTo(jdbc.queryForObject("SELECT paid_amount FROM orders WHERE code = 'HD00000110'", BigDecimal.class)));
        // san pham da xoa: giu ten, khong lien ket; ten bo khoang trang thua
        assertNull(jdbc.queryForObject("SELECT product_id FROM order_item WHERE product_name = 'Hàng đã xóa'", Long.class));
        assertEquals("Tôn hoa sen 0.4", jdbc.queryForObject("SELECT name FROM product WHERE id = 1", String.class));

        // ban moi tiep tuc so cu + ID tu tang khong dung ID cu
        String draft = mvc.perform(post("/api/orders").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString().replaceAll("^\\{\"id\":(\\d+).*", "$1");
        mvc.perform(post("/api/orders/" + draft + "/items").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":9,\"quantity\":1}")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + draft + "/checkout").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerId\":1030,\"paidAmount\":0,\"expectedPreviousDebt\":349862778}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("HD00000121"));
        assertEquals(0, new BigDecimal("350082778").compareTo(jdbc.queryForObject("SELECT debt FROM customer WHERE id = 1030", BigDecimal.class)));
        mvc.perform(post("/api/customers").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Khách mới\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(2001));

        // da co du lieu -> khoa, khong nhap lai / kiem tra thu duoc nua
        mvc.perform(get("/api/admin/legacy-import").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.canImport").value(false)).andExpect(jsonPath("$.last.report").value(org.hamcrest.Matchers.containsString("KHỚP HẾT")));
        mvc.perform(post("/api/admin/legacy-import?dryRun=true").header("Authorization", "Bearer " + admin).content(DATA))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/admin/legacy-import").header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$.canReset").value(true));
    }

    @Test
    @Order(4)
    void doiChieu_batDuocSaiLech() throws Exception {
        LegacyData d = om.readValue(DATA, LegacyData.class);
        // database lúc nay co them HD00000121 + khach moi (test truoc) -> ban dau da lech; sua them de chac bat duoc tung loai
        jdbc.update("UPDATE product SET price = price + 1 WHERE id = 9");
        jdbc.update("UPDATE order_item SET line_total = line_total + 1000 WHERE product_name = 'Bánh xe tủ'");
        jdbc.update("UPDATE orders SET created_at = DATEADD('HOUR', 1, created_at) WHERE code = 'HD00000007'");
        var r = verifier.verify(d);
        assertFalse(r.ok());
        String all = String.join("\n", r.errors());
        assertTrue(all.contains("ID 9 giá"), all);
        assertTrue(all.contains("HD00000100 dòng 1 thành tiền"), all);
        assertTrue(all.contains("HD00000007 ngày giờ"), all);
        assertTrue(all.contains("TỔNG NỢ"), all);          // no chu hung da tang do ban moi
        assertTrue(all.contains("số hóa đơn"), all);       // co them HD00000121
    }

    @Test
    @Order(5)
    void xoaToanBo_sauKhiNhap_canMatKhauVaMa() throws Exception {
        String qt = twoFactorAdmin();
        assertTrue(count("orders") > 0);
        reset(qt, "qt123456", rc.get(1)).andExpect(status().isBadRequest()); // ma du phong da dung -> khong dung lai duoc
        reset(qt, "qt123456", rc.get(2)).andExpect(status().isOk());
        for (String t : LegacyImportService.MUST_BE_EMPTY) assertEquals(0, count(t), t + " phai trong");
        assertEquals(0, count("legacy_import_log"), "xoa ca nhat ky -> nhap lai duoc");
        assertTrue(users.findByUsername("qt_legacy").isPresent() && users.findByUsername("admin").isPresent(), "giu tai khoan");
        mvc.perform(get("/api/admin/legacy-import").header("Authorization", "Bearer " + qt))
                .andExpect(jsonPath("$.canImport").value(true)).andExpect(jsonPath("$.canReset").value(false));
        // nhap lai duoc
        mvc.perform(post("/api/admin/legacy-import?dryRun=false").header("Authorization", "Bearer " + qt).content(DATA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.committed").value(true));
    }
}

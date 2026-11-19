package com.shop.order;

import com.shop.auth.AppUser;
import com.shop.auth.AppUserRepository;
import com.shop.customer.Customer;
import com.shop.customer.CustomerRepository;
import com.shop.customer.DebtLedger;
import com.shop.customer.DebtLedgerRepository;
import com.shop.customer.DebtService;
import com.shop.product.Product;
import com.shop.product.ProductRepository;
import com.shop.stock.ReceiptService;
import com.shop.stock.StockBalanceRepository;
import com.shop.stock.StockMovementRepository;
import com.shop.stock.StockService;
import com.shop.supplier.Supplier;
import com.shop.supplier.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sua hoa don da chot: tien hang, tong thanh toan, con no, cong no khach, so no, kho, lich su - va luong huy sau khi sua.
 * Bat bien kiem moi buoc: no khach = dong cuoi so no; ton kho = tong so kho.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderRevisionTest {
    @Autowired OrderService orders;
    @Autowired OrderRevisionService revisions;
    @Autowired OrderRepository orderRepo;
    @Autowired ProductRepository products;
    @Autowired CustomerRepository customers;
    @Autowired DebtLedgerRepository ledger;
    @Autowired DebtService debts;
    @Autowired StockService stock;
    @Autowired StockBalanceRepository balances;
    @Autowired StockMovementRepository movements;
    @Autowired ReceiptService receipts;
    @Autowired SupplierRepository suppliers;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    Product ton, hop, vit;
    Customer khach;

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", null));
        ton = product("Ton sua " + System.nanoTime(), "m2", "100000");
        hop = product("Hop sua " + System.nanoTime(), "cay", "200000");
        vit = product("Vit sua " + System.nanoTime(), "tui", "50000");
        Supplier s = new Supplier();
        s.setName("NCC sua " + System.nanoTime());
        s = suppliers.save(s);
        for (Product p : List.of(ton, hop, vit)) { // nhap kho 100 moi thu, gia von = 60% gia ban
            var r = receipts.create(new ReceiptService.ReceiptRequest(s.getId(), null, BigDecimal.ZERO, List.of(
                    new ReceiptService.LineRequest(p.getId(), null, new BigDecimal("100"), p.getPrice().multiply(new BigDecimal("0.6")), null))), "admin");
            receipts.post(r.getId(), "admin");
        }
        Customer c = new Customer();
        c.setName("Khach sua " + System.nanoTime());
        khach = customers.save(c);
    }

    private Product product(String name, String unit, String price) {
        Product p = new Product();
        p.setName(name); p.setUnit(unit); p.setPrice(new BigDecimal(price));
        p = products.save(p);
        stock.ensureBalance(p.getId());
        return p;
    }

    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static void eq(String expected, BigDecimal actual) { assertEquals(0, d(expected).compareTo(actual), "expected " + expected + " got " + actual); }
    private BigDecimal onHand(Product p) { return balances.findById(p.getId()).orElseThrow().getOnHand(); }
    private BigDecimal debt() { return customers.findById(khach.getId()).orElseThrow().getDebt(); }

    private void invariants() {
        for (Product p : List.of(ton, hop, vit)) assertEquals(0, movements.sumQty(p.getId()).compareTo(onHand(p)), "ton kho != tong so kho");
        List<DebtLedger> l = ledger.findAll().stream().filter(x -> x.getCustomerId().equals(khach.getId())).toList();
        if (!l.isEmpty()) eq(debt().toPlainString(), l.get(l.size() - 1).getBalanceAfter());
    }

    /** Ban chiu cho khach: ton 10 m2 + hop 2 cay = 1.400.000, tra 400.000 -> con no 1.000.000 */
    private Order sellOnCredit() {
        Order o = orders.createDraft("nv");
        orders.addItem(o.getId(), new OrderService.ItemRequest(ton.getId(), d("10"), 0, null, null));
        orders.addItem(o.getId(), new OrderService.ItemRequest(hop.getId(), d("2"), 0, null, null));
        return orders.checkout(o.getId(), new OrderService.CheckoutRequest(khach.getId(), null, null, null, null, d("400000"), null));
    }

    private List<OrderRevisionService.ReviseItem> keep(Order o) {
        return reload(o).getItems().stream().map(i ->
                new OrderRevisionService.ReviseItem(i.getId(), null, i.getQuantity(), i.getSheets(), i.getFactor(), i.getUnitPrice())).toList();
    }

    private OrderRevisionService.ReviseItem item(OrderItem i, String qty, String price) {
        return new OrderRevisionService.ReviseItem(i.getId(), null, d(qty), i.getSheets(), i.getFactor(), price == null ? i.getUnitPrice() : d(price));
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager tm;

    /** Doc lai hoa don kem dong hang (ngoai transaction cua service). */
    private Order reload(Order o) {
        return new org.springframework.transaction.support.TransactionTemplate(tm).execute(st -> {
            Order x = orderRepo.findById(o.getId()).orElseThrow();
            x.getItems().size();
            return x;
        });
    }

    @Test
    void tangSoLuong_vaDoiGia_tinhLaiTienNoKho() {
        Order o = sellOnCredit();
        eq("1000000", debt());
        eq("90", onHand(ton));
        var items = reload(o).getItems();
        OrderItem t = items.stream().filter(i -> i.getProductId().equals(ton.getId())).findFirst().orElseThrow();
        OrderItem h = items.stream().filter(i -> i.getProductId().equals(hop.getId())).findFirst().orElseThrow();

        // xem truoc: khong doi gi
        var req = new OrderRevisionService.ReviseRequest(List.of(item(t, "12", null), item(h, "2", "190000")), null, d("400000"), "giao them", "khach lay them 2 m2");
        var e = revisions.preview(o.getId(), req);
        eq("1580000", e.itemsTotalAfter());   // 12*100k + 2*190k
        eq("1180000", e.debtAfterAfter());
        eq("180000", e.debtChange());
        eq("1000000", debt());
        eq("90", onHand(ton));

        Order r = revisions.revise(o.getId(), req);
        assertEquals(o.getCode(), r.getCode(), "giu ma hoa don");
        eq("1580000", r.getItemsTotal());
        eq("1580000", r.getTotalPayable());
        eq("400000", r.getPaidAmount());
        eq("1180000", r.getDebtAfter());
        assertEquals("giao them", r.getNote());
        eq("1180000", debt());
        eq("88", onHand(ton));   // xuat them 2
        eq("98", onHand(hop));
        DebtLedger last = ledger.findAll().stream().filter(x -> x.getCustomerId().equals(khach.getId())).reduce((a, b) -> b).orElseThrow();
        assertEquals(DebtLedger.Type.REVISE, last.getType());
        eq("180000", last.getAmount());
        // gia von dong ton = 12 x 60.000
        eq("720000", reload(o).getItems().stream().filter(i -> i.getProductId().equals(ton.getId())).findFirst().orElseThrow().getCostTotal());
        var hist = revisions.history(o.getId());
        assertEquals(1, hist.size());
        assertEquals("khach lay them 2 m2", hist.get(0).reason());
        assertTrue(hist.get(0).before().contains("1.400.000") && hist.get(0).after().contains("1.580.000"), hist.get(0).after());
        invariants();
    }

    @Test
    void xoaDong_themDong_doiSanPham_vaHuySauKhiSua() {
        Order o = sellOnCredit();
        var items = reload(o).getItems();
        OrderItem t = items.stream().filter(i -> i.getProductId().equals(ton.getId())).findFirst().orElseThrow();
        // bo dong hop, them 4 tui vit, ton giu nguyen; khach tra them 100k luc do
        Order r = revisions.revise(o.getId(), new OrderRevisionService.ReviseRequest(List.of(
                item(t, "10", null), new OrderRevisionService.ReviseItem(null, vit.getId(), d("4"), 0, null, null)),
                null, d("500000"), null, "tra lai hop"));
        assertEquals(2, r.getItems().size());
        eq("1200000", r.getItemsTotal());     // 1.000.000 + 200.000
        eq("700000", r.getDebtAfter());
        eq("700000", debt());
        eq("100", onHand(hop));  // nhap lai 2 hop
        eq("96", onHand(vit));
        eq("90", onHand(ton));
        invariants();

        // sua lan 2: doi dong ton sang hop (doi san pham)
        OrderItem t2 = reload(o).getItems().stream().filter(i -> i.getProductId().equals(ton.getId())).findFirst().orElseThrow();
        OrderItem v2 = reload(o).getItems().stream().filter(i -> i.getProductId().equals(vit.getId())).findFirst().orElseThrow();
        revisions.revise(o.getId(), new OrderRevisionService.ReviseRequest(List.of(
                new OrderRevisionService.ReviseItem(t2.getId(), hop.getId(), d("3"), 0, null, null), item(v2, "4", null)),
                null, d("500000"), null, null));
        eq("100", onHand(ton));
        eq("97", onHand(hop));
        eq("300000", debt());   // 600k + 200k - 500k
        invariants();

        // huy sau 2 lan sua: kho ve dung nhu truoc khi ban, no ve 0
        orders.cancel(o.getId());
        eq("100", onHand(ton));
        eq("100", onHand(hop));
        eq("100", onHand(vit));
        eq("0", debt());
        invariants();
        assertThrows(Exception.class, () -> revisions.revise(o.getId(), new OrderRevisionService.ReviseRequest(keep(o), null, d("0"), null, null)),
                "hoa don da huy khong sua duoc");
    }

    @Test
    void giamTien_khiKhachDaTraHet_traLaiTienMat() {
        Order o = sellOnCredit();
        debts.pay(khach.getId(), d("1000000"), "tra het");
        eq("0", debt());
        OrderItem t = reload(o).getItems().stream().filter(i -> i.getProductId().equals(ton.getId())).findFirst().orElseThrow();
        OrderItem h = reload(o).getItems().stream().filter(i -> i.getProductId().equals(hop.getId())).findFirst().orElseThrow();
        // giam ton 10 -> 7 (-300.000): khach khong con no -> tra lai 300.000 tien mat, no van 0
        var e = revisions.preview(o.getId(), new OrderRevisionService.ReviseRequest(List.of(item(t, "7", null), item(h, "2", null)), null, d("400000"), null, null));
        eq("300000", e.refund());
        eq("0", e.debtChange());
        Order r = revisions.revise(o.getId(), new OrderRevisionService.ReviseRequest(List.of(item(t, "7", null), item(h, "2", null)), null, d("400000"), null, null));
        eq("0", debt());
        eq("300000", r.getRefundAmount());
        eq("700000", r.getDebtAfter());
        invariants();
    }

    @Test
    void khachLe_phaiTraDu() {
        Order o = orders.createDraft("nv");
        orders.addItem(o.getId(), new OrderService.ItemRequest(vit.getId(), d("2"), 0, null, null));
        o = orders.checkout(o.getId(), new OrderService.CheckoutRequest(null, "Anh A", null, null, null, d("100000"), null));
        OrderItem v = reload(o).getItems().get(0);
        Long id = o.getId();
        var tooLittle = new OrderRevisionService.ReviseRequest(List.of(item(v, "3", null)), null, d("100000"), null, null);
        var ex = assertThrows(com.shop.common.ApiException.class, () -> revisions.revise(id, tooLittle));
        assertTrue(ex.getMessage().contains("Khách lẻ phải trả đủ 150.000"), ex.getMessage());
        Order r = revisions.revise(id, new OrderRevisionService.ReviseRequest(List.of(item(v, "3", null)), d("20000"), d("170000"), null, null));
        eq("150000", r.getItemsTotal());
        eq("170000", r.getTotalPayable());
        eq("0", r.getDebtAfter());
        eq("97", onHand(vit));
    }

    @Test
    void hoaDonCu_chuaTungXuatKho_chiTruPhanChenhLech() {
        // hoa don nhap tu phan mem cu: khong co dong so kho
        Order o = sellOnCredit();
        jdbc.update("DELETE FROM stock_movement WHERE ref_type = 'ORDER' AND ref_id = ?", o.getId());
        jdbc.update("UPDATE stock_balance SET on_hand = on_hand + 10 WHERE product_id = ?", ton.getId());
        jdbc.update("UPDATE stock_balance SET on_hand = on_hand + 2 WHERE product_id = ?", hop.getId());
        eq("100", onHand(ton));
        OrderItem t = reload(o).getItems().stream().filter(i -> i.getProductId().equals(ton.getId())).findFirst().orElseThrow();
        OrderItem h = reload(o).getItems().stream().filter(i -> i.getProductId().equals(hop.getId())).findFirst().orElseThrow();
        revisions.revise(o.getId(), new OrderRevisionService.ReviseRequest(List.of(item(t, "11", null), item(h, "2", null)), null, d("400000"), null, null));
        eq("99", onHand(ton));    // chi tru them 1 m2, khong tru lai 10 m2 da ban tu truoc
        eq("100", onHand(hop));
        assertNull(reload(o).getItems().get(0).getCostTotal(), "hoa don chua tung xuat kho: khong ro gia von");
        orders.cancel(o.getId());
        eq("100", onHand(ton));   // huy: chi tra lai phan he thong da tru
    }

    @Test
    void quyen_vaLoiDauVao() throws Exception {
        Order o = sellOnCredit();
        if (users.findByUsername("nv_sua").isEmpty()) {
            AppUser u = new AppUser();
            u.setUsername("nv_sua"); u.setPasswordHash(encoder.encode("nv123456")); u.setFullName("NV"); u.setRole("STAFF");
            users.save(u);
        }
        String staff = mvc.perform(post("/api/auth/login").header("X-Forwarded-For", "10.8.0." + (int) (Math.random() * 250))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"nv_sua\",\"password\":\"nv123456\"}"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        String admin = mvc.perform(post("/api/auth/login").header("X-Forwarded-For", "10.8.1." + (int) (Math.random() * 250))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        String body = "{\"items\":[{\"id\":" + reload(o).getItems().get(0).getId() + ",\"quantity\":1}],\"paidAmount\":0}";
        for (String path : List.of("/revise", "/revise-preview"))
            mvc.perform(post("/api/orders/" + o.getId() + path).header("Authorization", "Bearer " + staff)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/" + o.getId() + "/revisions").header("Authorization", "Bearer " + staff)).andExpect(status().isForbidden());
        // quan tri: hop le qua API
        mvc.perform(post("/api/orders/" + o.getId() + "/revise-preview").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.itemsTotalAfter").value(100000));
        // het dong / dong cua hoa don khac / so luong 0
        mvc.perform(post("/api/orders/" + o.getId() + "/revise").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[],\"paidAmount\":0}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders/" + o.getId() + "/revise").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"id\":999999,\"quantity\":1}],\"paidAmount\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("không thuộc")));
        mvc.perform(post("/api/orders/" + o.getId() + "/revise").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("\"quantity\":1", "\"quantity\":0"))).andExpect(status().isBadRequest());
        eq("1000000", debt());
        // sao ke cong no hien "Sua HD"
        mvc.perform(post("/api/orders/" + o.getId() + "/revise").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(o.getCode()));
        mvc.perform(get("/api/customers/" + khach.getId() + "/debt-ledger").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.items[0].description").value(org.hamcrest.Matchers.startsWith("Sửa HĐ")));
        mvc.perform(get("/api/orders/" + o.getId() + "/revisions").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].revisedBy").isNotEmpty());
        invariants();
    }
}

package com.shop.order;

import com.shop.common.ApiException;
import com.shop.customer.Customer;
import com.shop.customer.CustomerRepository;
import com.shop.customer.DebtLedger;
import com.shop.customer.DebtLedgerRepository;
import com.shop.customer.DebtService;
import com.shop.product.Product;
import com.shop.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cac kich ban ban hang thuc te + bat bien ve tien (cong no luon khop so cai, hoa don luon cong dung).
 * So tien mong doi tinh tay trong test (khong goi LineCalculator) de khong "tu kiem tra chinh minh".
 */
@SpringBootTest
@ActiveProfiles("test")
class SaleScenariosTest {
    @Autowired OrderService service;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired CustomerRepository customers;
    @Autowired DebtLedgerRepository ledger;
    @Autowired DebtService debts;

    Product tonLanh, hopSat, nhom;

    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static BigDecimal d(long v) { return BigDecimal.valueOf(v); }
    private static void eq(String expected, BigDecimal actual, String what) {
        assertEquals(0, d(expected).compareTo(actual), what + ": mong doi " + expected + ", thuc te " + actual);
    }

    private Product product(String name, String unit, String price) {
        Product p = new Product();
        p.setName(name); p.setUnit(unit); p.setPrice(d(price));
        return products.save(p);
    }

    private Customer customer(String name, String debt) {
        Customer c = new Customer();
        c.setName(name);
        c = customers.save(c);
        if (d(debt).signum() > 0) c = debts.setDebt(c.getId(), d(debt), "No dau ky");
        return c;
    }

    private Customer reload(Customer c) { return customers.findById(c.getId()).orElseThrow(); }

    private OrderService.ItemRequest item(Product p, String qty, int sheets, String factor, String price) {
        return new OrderService.ItemRequest(p.getId(), d(qty), sheets, factor == null ? null : d(factor), price == null ? null : d(price));
    }

    private Order pay(Order o, Customer c, String ship, String paid) {
        return service.checkout(o.getId(), new OrderService.CheckoutRequest(
                c == null ? null : c.getId(), c == null ? "Khach le" : null, null, null, d(ship), d(paid), null));
    }

    @BeforeEach
    void setup() {
        tonLanh = product("Tôn lạnh 0,4 ly", "m", "95000");
        hopSat = product("Hộp sắt 40x80 dày 1,4", "cây", "285000");
        nhom = product("Nhôm Xingfa hệ 55", "kg", "120000");
    }

    @Test
    void tonNhieuDoan_sat_nhom_khachNoCu_traMotPhan() {
        Customer c = customer("Anh Tuấn", "12450000");
        Order o = service.createDraft("thu");
        o = service.addItem(o.getId(), item(tonLanh, "4.7", 2, null, null)); // 4,7 x 2 x 1,08 = 10,152 m x 95000 = 964440
        o = service.addItem(o.getId(), item(tonLanh, "4.5", 2, null, null)); // 9,72 m -> 923400
        o = service.addItem(o.getId(), item(tonLanh, "3", 1, null, null));   // 3,24 m -> 307800
        o = service.addItem(o.getId(), item(hopSat, "6", 0, null, null));    // 6 cay -> 1710000
        o = service.addItem(o.getId(), item(nhom, "2.375", 0, null, "118000")); // gia thoa thuan: 280250
        eq("4185890", o.getItemsTotal(), "Tien hang");

        Order paid = pay(o, c, "150000", "3000000");
        eq("4335890", paid.getItemsTotal().add(paid.getShippingFee()), "Tien hang + ship");
        eq("12450000", paid.getPreviousDebt(), "No cu");
        eq("16785890", paid.getTotalPayable(), "Tong phai tra");     // 4185890 + 150000 + 12450000
        eq("13785890", paid.getDebtAfter(), "Con no");               // - 3000000
        eq("13785890", reload(c).getDebt(), "No khach");
        assertLedgerMatches(reload(c));

        // hoa don sau mang no cu sang
        Order next = service.addItem(service.createDraft("thu").getId(), item(hopSat, "1", 0, null, null));
        Order paid2 = pay(next, reload(c), "0", "0");
        eq("13785890", paid2.getPreviousDebt(), "No cu don sau");
        eq("14070890", paid2.getDebtAfter(), "Con no don sau");
    }

    @Test
    void suaVaXoaDong_tinhLaiTienHang() {
        Order o = service.createDraft("thu");
        o = service.addItem(o.getId(), item(tonLanh, "4.2", 12, null, null)); // 54,432 x 95000 = 5171040
        o = service.addItem(o.getId(), item(hopSat, "6", 0, null, null));     // 1710000
        eq("6881040", o.getItemsTotal(), "Tien hang");

        Long tonId = o.getItems().get(0).getId();
        o = service.updateItem(o.getId(), tonId, item(tonLanh, "4.2", 10, "1", null)); // 42 x 95000 = 3990000
        eq("42", o.getItems().get(0).getTotalQuantity(), "Tong SL sau sua");
        eq("5700000", o.getItemsTotal(), "Tien hang sau sua");

        o = service.removeItem(o.getId(), o.getItems().get(1).getId());
        assertEquals(1, o.getItems().size());
        eq("3990000", o.getItemsTotal(), "Tien hang sau xoa");
        eq("3990000", service.get(o.getId()).getItemsTotal(), "Tien hang da luu");
    }

    @Test
    void giaChupLaiLucBan_suaGiaSanPhamKhongDoiHoaDon() {
        Order o = service.addItem(service.createDraft("thu").getId(), item(tonLanh, "10", 0, null, null)); // 950000
        tonLanh.setPrice(d("99000"));
        products.save(tonLanh);
        Order paid = pay(o, null, "0", "950000");
        eq("950000", paid.getItemsTotal(), "Hoa don giu gia luc them");
        eq("95000", paid.getItems().get(0).getUnitPrice(), "Don gia dong");
        tonLanh.setName("Tôn lạnh (đổi tên)");
        products.save(tonLanh);
        assertEquals("Tôn lạnh 0,4 ly", service.get(paid.getId()).getItems().get(0).getProductName());
    }

    @Test
    void dauVaoSai_biTuChoi_hoaDonKhongDoi() {
        Order o = service.addItem(service.createDraft("thu").getId(), item(hopSat, "1", 0, null, null));
        Long id = o.getId();
        List<OrderService.ItemRequest> bad = List.of(
                item(hopSat, "0", 0, null, null),          // SL 0
                item(hopSat, "-2", 0, null, null),         // SL am
                item(hopSat, "1", -1, null, null),         // so tam am
                item(hopSat, "1", 0, null, "-1000"),       // gia am
                item(tonLanh, "5", 0, "1.08", null),       // xoa so tam con he so -> Tong SL 0
                item(tonLanh, "0.0004", 0, null, null));   // Tong SL lam tron ve 0
        for (var r : bad) assertThrows(ApiException.class, () -> service.addItem(id, r), r.toString());
        assertThrows(ApiException.class, () -> service.addItem(id, new OrderService.ItemRequest(null, d("1"), 0, null, null)));

        hopSat.setActive(false); // ngung ban
        products.save(hopSat);
        assertThrows(ApiException.class, () -> service.addItem(id, item(hopSat, "1", 0, null, null)));

        Order after = service.get(id);
        assertEquals(1, after.getItems().size());
        eq("285000", after.getItemsTotal(), "Tien hang khong doi");

        assertThrows(ApiException.class, () -> pay(after, null, "-1", "0"));
        assertThrows(ApiException.class, () -> pay(after, null, "0", "-1"));
        assertEquals(Order.Status.DRAFT, service.get(id).getStatus());
    }

    @Test
    void khongChotDuocHaiLan_khongChotDonRong() {
        Order empty = service.createDraft("thu");
        assertThrows(ApiException.class, () -> pay(empty, null, "0", "0"));
        Customer c = customer("Chị Lan", "0");
        Order o = service.addItem(service.createDraft("thu").getId(), item(nhom, "1", 0, null, null));
        pay(o, c, "0", "0");
        assertThrows(ApiException.class, () -> pay(o, c, "0", "0"));
        eq("120000", reload(c).getDebt(), "No chi ghi 1 lan");
        assertLedgerMatches(reload(c));
    }

    @Test
    void traThua_traDuTienHang_ghiNoHet() {
        Customer c = customer("Công ty Minh Phát", "500000");
        // tra thua: con no 0, tien thua khong thanh no am
        Order a = pay(service.addItem(service.createDraft("thu").getId(), item(hopSat, "1", 0, null, null)), c, "0", "1000000");
        eq("785000", a.getTotalPayable(), "Tong phai tra");
        eq("0", a.getDebtAfter(), "Con no");
        eq("0", reload(c).getDebt(), "No khach");
        // "Tra tien hang": chi tra hang + ship, no cu giu nguyen
        customer(c, "300000");
        Order b = pay(service.addItem(service.createDraft("thu").getId(), item(nhom, "2", 0, null, null)), reload(c), "50000", "290000");
        eq("300000", b.getDebtAfter(), "No cu giu nguyen");
        // "Ghi no het"
        Order g = pay(service.addItem(service.createDraft("thu").getId(), item(nhom, "1", 0, null, null)), reload(c), "0", "0");
        eq("420000", g.getDebtAfter(), "Ghi no het");
        assertLedgerMatches(reload(c));
    }

    private void customer(Customer c, String debt) { debts.setDebt(c.getId(), d(debt), "Dat lai no"); }

    @Test
    void khachLe_traThieu_khongDungVaoCongNoAi() {
        Customer c = customer("Khách quen", "700000");
        Order o = pay(service.addItem(service.createDraft("thu").getId(), item(tonLanh, "4", 3, null, null)), null, "0", "1000000");
        // 4 x 3 x 1,08 = 12,96 x 95000 = 1231200
        eq("1231200", o.getTotalPayable(), "Tong phai tra");
        eq("231200", o.getDebtAfter(), "Con lai ghi tren hoa don");
        assertNull(o.getCustomerId());
        eq("700000", reload(c).getDebt(), "No khach khac khong doi");
    }

    @Test
    void huyDonCu_saukhiDaCoDonMoi_noVanKhopSoCai() {
        Customer c = customer("Anh Hùng", "0");
        Order a = pay(service.addItem(service.createDraft("thu").getId(), item(hopSat, "2", 0, null, null)), c, "0", "100000"); // no 470000
        Order b = pay(service.addItem(service.createDraft("thu").getId(), item(nhom, "1", 0, null, null)), reload(c), "0", "0"); // no 590000
        eq("590000", reload(c).getDebt(), "No truoc huy");
        service.cancel(a.getId());
        eq("120000", reload(c).getDebt(), "Huy don A tru dung phan A lam tang no");
        assertLedgerMatches(reload(c));
        assertThrows(ApiException.class, () -> service.cancel(a.getId())); // khong huy 2 lan
        service.cancel(b.getId());
        eq("0", reload(c).getDebt(), "Huy het ve 0");
        assertLedgerMatches(reload(c));
    }

    /**
     * Hien trang (chua chot nghiep vu): khach mua chiu, tra het no, roi hoa don bi huy (tra hang) -> no thanh am.
     * Test nay ghi lai hanh vi hien tai; doi khi chot cach xu ly (hoan tien / giu lam tien du / chan huy).
     */
    @Test
    void huyDonSauKhiKhachDaTraNo_noBiAm() {
        Customer c = customer("Khách trả hàng", "0");
        Order o = pay(service.addItem(service.createDraft("thu").getId(), item(hopSat, "2", 0, null, null)), c, "0", "0"); // no 570000
        debts.pay(c.getId(), d("570000"), "Tra het");
        service.cancel(o.getId());
        eq("-570000", reload(c).getDebt(), "No sau huy (hien trang)");
    }

    /** Mo phong 300 thao tac ngau nhien, sau moi buoc kiem tra bat bien ve tien. */
    @Test
    void moPhongNgauNhien_batBienCongNo() {
        Random rnd = new Random(20261116);
        List<Customer> cs = List.of(customer("KH A", "0"), customer("KH B", "1500000"), customer("KH C", "0"));
        List<Product> ps = List.of(tonLanh, hopSat, nhom);
        List<Long> paidIds = new ArrayList<>();
        for (int step = 0; step < 300; step++) {
            int op = rnd.nextInt(10);
            Customer c = reload(cs.get(rnd.nextInt(cs.size())));
            if (op < 6) { // ban hang
                Order o = service.createDraft("thu");
                BigDecimal expectItems = BigDecimal.ZERO;
                for (int k = 0, n = 1 + rnd.nextInt(4); k < n; k++) {
                    Product p = ps.get(rnd.nextInt(ps.size()));
                    BigDecimal q = BigDecimal.valueOf(1 + rnd.nextInt(5000), 3).add(BigDecimal.ONE); // 1,001 .. 6
                    int sheets = rnd.nextBoolean() ? 0 : 1 + rnd.nextInt(20);
                    BigDecimal raw = sheets == 0 ? q : q.multiply(d(sheets)).multiply(d("1.08"));
                    expectItems = expectItems.add(p.getPrice().multiply(raw).setScale(0, RoundingMode.HALF_EVEN));
                    o = service.addItem(o.getId(), new OrderService.ItemRequest(p.getId(), q, sheets, null, null));
                }
                eq(expectItems.toPlainString(), o.getItemsTotal(), "Tien hang buoc " + step);
                BigDecimal ship = d(rnd.nextInt(3) * 50000L);
                BigDecimal before = c.getDebt();
                BigDecimal payable = expectItems.add(ship).add(before);
                BigDecimal paidAmt = switch (rnd.nextInt(4)) {
                    case 0 -> BigDecimal.ZERO;
                    case 1 -> payable;                                      // tra du
                    case 2 -> payable.add(d(100000));                       // tra thua
                    default -> payable.multiply(d(rnd.nextInt(100))).divide(d(100), 0, RoundingMode.DOWN);
                };
                Order paid = pay(o, c, ship.toPlainString(), paidAmt.toPlainString());
                eq(payable.toPlainString(), paid.getTotalPayable(), "Tong phai tra buoc " + step);
                eq(payable.subtract(paidAmt).max(BigDecimal.ZERO).toPlainString(), paid.getDebtAfter(), "Con no buoc " + step);
                eq(paid.getDebtAfter().toPlainString(), reload(c).getDebt(), "No khach = con no hoa don, buoc " + step);
                paidIds.add(paid.getId());
            } else if (op < 8) { // thu no
                if (c.getDebt().signum() > 0) {
                    BigDecimal amt = c.getDebt().multiply(d(1 + rnd.nextInt(100))).divide(d(100), 0, RoundingMode.UP).min(c.getDebt());
                    debts.pay(c.getId(), amt, "Thu no");
                    eq(c.getDebt().subtract(amt).toPlainString(), reload(c).getDebt(), "Thu no buoc " + step);
                }
            } else if (!paidIds.isEmpty()) { // huy mot hoa don ngau nhien
                Long id = paidIds.remove(rnd.nextInt(paidIds.size()));
                Order o = service.get(id);
                if (o.getCustomerId() != null) {
                    Customer oc = customers.findById(o.getCustomerId()).orElseThrow();
                    BigDecimal net = o.getDebtAfter().subtract(o.getPreviousDebt());
                    // CHUA CHOT NGHIEP VU: huy don sau khi khach da tra bot no cua chinh don do -> no am.
                    // Tam bo qua truong hop nay trong mo phong; xem test huyDonSauKhiKhachDaTraNo_noBiAm.
                    if (oc.getDebt().compareTo(net) < 0) continue;
                    service.cancel(id);
                    eq(oc.getDebt().subtract(net).toPlainString(), reload(oc).getDebt(), "Huy don buoc " + step);
                }
            }
            for (Customer x : cs) assertLedgerMatches(reload(x));
        }
    }

    /** Bat bien: no khach = tong so cai = so du dong cuoi, va khong bao gio thanh no am do ban hang. */
    private void assertLedgerMatches(Customer c) {
        List<DebtLedger> rows = ledger.findByCustomerIdOrderByIdDesc(c.getId());
        BigDecimal sum = rows.stream().map(DebtLedger::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        eq(sum.toPlainString(), c.getDebt(), "No " + c.getName() + " = tong so cai");
        if (!rows.isEmpty()) eq(rows.get(0).getBalanceAfter().toPlainString(), c.getDebt(), "No " + c.getName() + " = so du dong cuoi");
        assertTrue(c.getDebt().signum() >= 0, "No " + c.getName() + " bi am: " + c.getDebt() + " (dong cuoi: " + (rows.isEmpty() ? "-" : rows.get(0).getType() + " " + rows.get(0).getNote()) + ")");
    }
}

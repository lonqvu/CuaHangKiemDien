package com.shop.customer;

import com.shop.common.ApiException;
import com.shop.order.Order;
import com.shop.order.OrderService;
import com.shop.product.Product;
import com.shop.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phieu thu no / sao ke: so lieu phai chinh xac de in cho khach.
 *  1) moi dong: con no dong truoc + tang - giam = con no dong nay; dong dau cua phieu bat dau tu 0 (hoac dong "mang sang")
 *  2) tong ket: no truoc khi thu - so thu = con no = con no dong cuoi
 *  3) in lai luc nao cung ra y nguyen (tru dau "DA HUY" khi phieu bi huy sau do)
 */
@SpringBootTest
@ActiveProfiles("test")
class DebtReceiptTest {
    @Autowired DebtService debts;
    @Autowired DebtStatementService statements;
    @Autowired CustomerRepository customers;
    @Autowired CustomerController customerController;
    @Autowired OrderService orders;
    @Autowired ProductRepository products;

    Product p;
    Customer c;

    private static BigDecimal d(long v) { return BigDecimal.valueOf(v); }

    @BeforeEach
    void setup() {
        p = new Product();
        p.setName("Hàng test phiếu thu"); p.setUnit("cây"); p.setPrice(d(100000));
        p = products.save(p);
        c = new Customer();
        c.setName("Khách phiếu thu " + UUID.randomUUID());
        c = customers.save(c);
    }

    private Order buy(long qty, long paid) {
        Order o = orders.createDraft("thu");
        o = orders.addItem(o.getId(), new OrderService.ItemRequest(p.getId(), d(qty), 0, null, null));
        return orders.checkout(o.getId(), new OrderService.CheckoutRequest(c.getId(), null, null, null, BigDecimal.ZERO, d(paid), null));
    }

    private DebtLedger collect(long amount) { return debts.pay(c.getId(), d(amount), null, DebtLedger.Method.CASH); }

    private BigDecimal debt() { return customers.findById(c.getId()).orElseThrow().getDebt(); }

    /** Bat bien in an: cong tru khop tung dong + tong ket khop. */
    static void assertReconciles(DebtStatementService.Doc doc, boolean isReceipt) {
        List<DebtStatementService.Line> ls = doc.lines();
        assertFalse(ls.isEmpty());
        DebtStatementService.Line first = ls.get(0);
        BigDecimal prev;
        int startAt;
        if (first.ledgerId() == null) { prev = first.balance(); startAt = 1; } // "So du mang sang" / "So du dau ky"
        else { prev = BigDecimal.ZERO; startAt = 0; }                         // phieu bat dau tu luc no = 0
        for (int i = startAt; i < ls.size(); i++) {
            var l = ls.get(i);
            BigDecimal inc = l.increase() == null ? BigDecimal.ZERO : l.increase();
            BigDecimal dec = l.decrease() == null ? BigDecimal.ZERO : l.decrease();
            assertEquals(0, prev.add(inc).subtract(dec).compareTo(l.balance()),
                    "Dong " + i + " '" + l.description() + "': " + prev + " + " + inc + " - " + dec + " != " + l.balance());
            assertTrue(l.increase() == null || l.decrease() == null, "Mot dong khong vua tang vua giam");
            prev = l.balance();
        }
        BigDecimal last = ls.get(ls.size() - 1).balance();
        var t = doc.totals();
        if (isReceipt) {
            assertEquals(0, t.get(0).amount().subtract(t.get(1).amount()).compareTo(t.get(2).amount()), "No truoc - thu = con no");
            assertEquals(0, t.get(2).amount().compareTo(last), "Con no = dong cuoi");
            assertTrue(ls.get(ls.size() - 1).highlight(), "Dong cuoi la lan thu nay (in dam)");
        } else {
            assertEquals(0, t.get(0).amount().add(t.get(1).amount()).subtract(t.get(2).amount()).compareTo(t.get(3).amount()),
                    "Dau ky + tang - giam = cuoi ky");
            assertEquals(0, t.get(3).amount().compareTo(last), "Cuoi ky = dong cuoi");
        }
    }

    @Test
    void inLai_raYNguyen_sauKhiCoThemPhatSinh() {
        buy(5, 0);                    // no 500.000
        DebtLedger pt = collect(200000);
        var snap = statements.receipt(pt.getId());
        assertReconciles(snap, true);

        buy(3, 100000);               // them phat sinh sau
        debts.setDebt(c.getId(), d(10000), "Giảm giá cuối năm");
        collect(5000);
        DebtLedger other = collect(5000); // no ve 0
        debts.voidPayment(other.getId(), "Thu trùng");

        assertEquals(snap, statements.receipt(pt.getId()), "In lai phieu cu phai giong het lan dau");
    }

    @Test
    void traThuaLucMua_phieuChiGhiPhanTruVaoNo() {
        buy(2, 0);                    // no 200.000
        Order o = buy(1, 1000000);    // phai tra 300.000, khach dua 1.000.000
        assertEquals(0, BigDecimal.ZERO.compareTo(o.getDebtAfter()));
        buy(1, 0);                    // no 100.000
        var doc = statements.receipt(collect(100000).getId());
        assertReconciles(doc, true);
        var tra = doc.lines().stream().filter(l -> l.description().startsWith("Trả khi mua")).toList();
        // dot no hien tai bat dau sau lan no ve 0 -> chi con dong mua cuoi + lan thu
        assertTrue(tra.isEmpty(), "Lan tra thua thuoc dot no truoc");
        var full = statements.statement(c.getId(), null, null);
        assertReconciles(full, false);
        var traThua = full.lines().stream().filter(l -> l.description().startsWith("Trả khi mua")).findFirst().orElseThrow();
        assertEquals(0, d(300000).compareTo(traThua.decrease()), "Chi ghi phan tru vao no (300.000), khong ghi 1.000.000");
    }

    @Test
    void huyPhieuThu_noTangLai_phieuCoDauDaHuy_khongHuyHaiLan() {
        buy(5, 0);
        DebtLedger pt = collect(300000);
        assertEquals(0, d(200000).compareTo(debt()));
        assertThrows(ApiException.class, () -> debts.voidPayment(pt.getId(), " "), "Bat buoc ly do");

        debts.voidPayment(pt.getId(), "Thu nhầm số tiền");
        assertEquals(0, d(500000).compareTo(debt()));
        var doc = statements.receipt(pt.getId());
        assertTrue(doc.voided());
        assertEquals("Thu nhầm số tiền", doc.voidReason());
        var ex = assertThrows(ApiException.class, () -> debts.voidPayment(pt.getId(), "lan 2"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());

        var rows = customerController.history(c.getId());
        assertTrue(rows.get(0).description().startsWith("Hủy phiếu thu " + pt.getReceiptNo() + ": Thu nhầm"), rows.get(0).description());
        var paidRow = rows.stream().filter(r -> pt.getReceiptNo().equals(r.receiptNo())).findFirst().orElseThrow();
        assertTrue(paidRow.voided());
        assertFalse(paidRow.canVoid());
        assertReconciles(statements.statement(c.getId(), null, null), false);

        // tien tra luc mua khong huy bang phieu (huy hoa don thay the)
        buy(1, 50000);
        Long atCheckout = customerController.history(c.getId()).stream().filter(r -> r.description().startsWith("Trả khi mua"))
                .findFirst().orElseThrow().id();
        assertThrows(ApiException.class, () -> debts.voidPayment(atCheckout, "x"));
    }

    @Test
    void dieuChinhNo_batBuocLyDo_hienThanhDong_khongCatLichSu() {
        buy(5, 0);                                    // 500.000
        assertThrows(ApiException.class, () -> debts.setDebt(c.getId(), d(450000), ""));
        debts.setDebt(c.getId(), d(450000), "Bớt 50.000 vì hàng lỗi");
        int before = customerController.history(c.getId()).size();
        debts.setDebt(c.getId(), d(450000), "Không đổi");  // khong doi -> khong ghi so
        assertEquals(before, customerController.history(c.getId()).size());

        var doc = statements.receipt(collect(450000).getId());
        assertReconciles(doc, true);
        assertEquals(List.of("Mua HĐ", "Điều chỉnh: Bớt 50.000 vì hàng lỗi", "Thu nợ"),
                doc.lines().stream().map(l -> l.description().replaceAll(" (HD|PT)\\d+.*", "")).toList());
    }

    @Test
    void lichSuDai_gopThanhSoDuMangSang() {
        for (int i = 0; i < 25; i++) buy(1, 0);       // 25 dong mua, no 2.500.000
        var doc = statements.receipt(collect(700000).getId());
        assertEquals(DebtStatementService.MAX_LINES + 1, doc.lines().size());
        assertEquals("Số dư mang sang", doc.lines().get(0).description());
        assertNull(doc.lines().get(0).ledgerId());
        assertReconciles(doc, true);
    }

    @Test
    void saoKe_theoKhoangNgay() {
        buy(4, 100000);
        collect(100000);
        LocalDate today = LocalDate.now();
        var all = statements.statement(c.getId(), today, today);
        assertReconciles(all, false);
        assertEquals(0, BigDecimal.ZERO.compareTo(all.totals().get(0).amount()), "Dau ky = 0");
        var tomorrow = statements.statement(c.getId(), today.plusDays(1), today.plusDays(2));
        assertEquals(1, tomorrow.lines().size(), "Chi co dong so du dau ky");
        assertEquals(0, debt().compareTo(tomorrow.totals().get(3).amount()));
        assertThrows(ApiException.class, () -> statements.statement(c.getId(), today, today.minusDays(1)));
    }

    @Test
    void chotDon_noCuDaBiMayKhacDoi_tuChoi() {
        buy(5, 0);                                     // 500.000
        Order o = orders.addItem(orders.createDraft("thu").getId(), new OrderService.ItemRequest(p.getId(), d(1), 0, null, null));
        collect(100000);                               // may khac vua thu no -> con 400.000
        Long id = o.getId();
        var ex = assertThrows(ApiException.class, () -> orders.checkout(id, new OrderService.CheckoutRequest(
                c.getId(), null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, null, d(500000))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(Order.Status.DRAFT, orders.get(id).getStatus());
        Order ok = orders.checkout(id, new OrderService.CheckoutRequest(c.getId(), null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, null, d(400000)));
        assertEquals(0, d(400000).compareTo(ok.getPreviousDebt()));
    }

    @Test
    void huyHoaDon_traLaiTien_hienTrenSaoKe() {
        Order o = buy(5, 0);
        collect(500000);
        orders.cancel(o.getId());
        var st = statements.statement(c.getId(), null, null);
        assertReconciles(st, false);
        String last = st.lines().get(st.lines().size() - 1).description();
        assertEquals("Hủy HĐ " + o.getCode() + " (đã trả lại khách 500,000)", last);
    }

    /** Ngau nhien 250 thao tac: moi phieu thu tung in deu cong tru khop va in lai giong het. */
    @Test
    void ngauNhien_moiPhieuDeuKhop_vaKhongDoi() {
        Random rnd = new Random(42);
        Map<Long, DebtStatementService.Doc> printed = new LinkedHashMap<>();
        List<Long> voidable = new ArrayList<>();
        for (int step = 0; step < 250; step++) {
            int op = rnd.nextInt(10);
            BigDecimal cur = debt();
            if (op < 4) buy(1 + rnd.nextInt(5), rnd.nextInt(3) == 0 ? rnd.nextInt(8) * 50000L : 0);
            else if (op < 8 && cur.signum() > 0) {
                long amt = Math.max(1, cur.longValue() * (1 + rnd.nextInt(100)) / 100);
                DebtLedger pt = collect(Math.min(amt, cur.longValue()));
                var doc = statements.receipt(pt.getId());
                assertReconciles(doc, true);
                printed.put(pt.getId(), doc);
                voidable.add(pt.getId());
            } else if (op == 8 && !voidable.isEmpty()) {
                Long id = voidable.remove(rnd.nextInt(voidable.size()));
                debts.voidPayment(id, "Ngẫu nhiên " + step);
                printed.put(id, statements.receipt(id)); // tu day phieu mang dau DA HUY
            } else if (op == 9) {
                debts.setDebt(c.getId(), d(rnd.nextInt(10) * 10000L), "Điều chỉnh " + step);
            }
        }
        assertTrue(printed.size() > 30, "Du nhieu phieu de kiem tra: " + printed.size());
        printed.forEach((id, doc) -> assertEquals(doc, statements.receipt(id), "Phieu " + doc.docNo() + " in lai khac lan dau"));
        assertReconciles(statements.statement(c.getId(), null, null), false);
        assertEquals(0, debt().compareTo(statements.statement(c.getId(), null, null).totals().get(3).amount()));
    }
}

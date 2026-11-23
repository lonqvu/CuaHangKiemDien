package com.shop.stock;

import com.shop.order.Order;
import com.shop.order.OrderService;
import com.shop.product.Product;
import com.shop.product.ProductRepository;
import com.shop.supplier.Supplier;
import com.shop.supplier.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class InventoryTest {
    @Autowired ReceiptService receipts;
    @Autowired OrderService orders;
    @Autowired StockService stock;
    @Autowired StockBalanceRepository balances;
    @Autowired StockMovementRepository movements;
    @Autowired ProductRepository products;
    @Autowired ProductUnitRepository units;
    @Autowired SupplierRepository suppliers;
    @Autowired StockCountController counts;

    Product ton;      // ban theo m, nhap theo kg
    Product thep;     // ban theo cay
    Supplier ncc;
    final UsernamePasswordAuthenticationToken admin = new UsernamePasswordAuthenticationToken("admin", null);

    @BeforeEach
    void setup() {
        ton = product("Ton test " + System.nanoTime(), "m", "92000");
        thep = product("Thep test " + System.nanoTime(), "cay", "145000");
        ProductUnit kg = new ProductUnit();
        kg.setProductId(ton.getId());
        kg.setUnit("kg");
        kg.setRatio(new BigDecimal("0.318"));
        units.save(kg);
        Supplier s = new Supplier();
        s.setName("NCC test " + System.nanoTime());
        ncc = suppliers.save(s);
    }

    private Product product(String name, String unit, String price) {
        Product p = new Product();
        p.setName(name);
        p.setUnit(unit);
        p.setPrice(new BigDecimal(price));
        p = products.save(p);
        stock.ensureBalance(p.getId());
        return p;
    }

    private BigDecimal onHand(Product p) { return balances.findById(p.getId()).orElseThrow().getOnHand(); }
    private BigDecimal avg(Product p) { return balances.findById(p.getId()).orElseThrow().getAvgCost(); }
    private static void eq(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " got " + actual); }

    /** Bat bien quan trong nhat: so du = tong so kho. */
    private void assertLedgerMatches(Product p) { assertEquals(0, movements.sumQty(p.getId()).compareTo(onHand(p)), "on_hand != sum(stock_movement)"); }

    private GoodsReceipt receive(Product p, String qty, String price, String paid) {
        var r = receipts.create(new ReceiptService.ReceiptRequest(ncc.getId(), null, new BigDecimal(paid),
                List.of(new ReceiptService.LineRequest(p.getId(), null, new BigDecimal(qty), new BigDecimal(price), null))), "admin");
        return receipts.post(r.getId(), "admin");
    }

    private Order sell(Product p, String qty, int sheets) {
        Order o = orders.createDraft("nv");
        orders.addItem(o.getId(), new OrderService.ItemRequest(p.getId(), new BigDecimal(qty), sheets, null, null));
        return orders.checkout(o.getId(), new OrderService.CheckoutRequest(null, "Khach le", null, null, null, new BigDecimal("999999999"), null));
    }

    @Test
    void receipts_movingAverageCost_andSupplierDebt() {
        receive(thep, "10", "100000", "0");          // 10 cay @100k, no NCC 1.000.000
        receive(thep, "30", "120000", "1000000");    // 30 cay @120k, tra 1tr -> no them 2.600.000
        eq("40", onHand(thep));
        eq("115000", avg(thep));                     // (10*100k + 30*120k) / 40
        eq("3600000", suppliers.findById(ncc.getId()).orElseThrow().getDebt());
        assertLedgerMatches(thep);
    }

    @Test
    void checkout_deductsMetersCut_notFactor_recordsCost_cancelReturnsExactly() {
        receive(ton, "100", "70000", "7000000");
        Order o = sell(ton, "3", 10);                // tinh tien 3m x 10 tam x 1.08 = 32.4; kho tru so met cat = 3 x 10 = 30
        eq("32.4", orders.get(o.getId()).getItems().get(0).getTotalQuantity());
        eq("70", onHand(ton));
        Order saved = orders.get(o.getId());
        eq("2100000", saved.getItems().get(0).getCostTotal()); // 30 m x 70.000

        orders.cancel(o.getId());
        eq("100", onHand(ton));
        eq("70000", avg(ton));
        assertLedgerMatches(ton);
    }

    @Test
    void sellingWithoutAnyCost_allowsNegative_costUnknown() {
        Order o = sell(thep, "5", 0);
        eq("-5", onHand(thep));
        assertNull(orders.get(o.getId()).getItems().get(0).getCostTotal(), "chua co gia von -> de trong");
        receive(thep, "20", "130000", "0");          // ton dang am: binh quan = gia nhap
        eq("15", onHand(thep));
        eq("130000", avg(thep));
        assertLedgerMatches(thep);
    }

    @Test
    void receipt_inKg_convertsToMeters_andCanOverrideActualQty() {
        var r = receipts.create(new ReceiptService.ReceiptRequest(ncc.getId(), null, BigDecimal.ZERO, List.of(
                new ReceiptService.LineRequest(ton.getId(), "kg", new BigDecimal("1000"), new BigDecimal("22000"), null),
                new ReceiptService.LineRequest(ton.getId(), "kg", new BigDecimal("500"), new BigDecimal("22000"), new BigDecimal("160")))), "admin");
        assertEquals(0, new BigDecimal("318").compareTo(r.getLines().get(0).getQty()), "1000 kg x 0.318 = 318 m");
        receipts.post(r.getId(), "admin");
        eq("478", onHand(ton));                      // 318 + 160 (do thuc te)
        eq("69037.6569", avg(ton));                  // 33.000.000 / 478
    }

    @Test
    void cancelReceipt_reversesStockAndSupplierDebt() {
        GoodsReceipt r = receive(thep, "10", "100000", "400000");
        eq("600000", suppliers.findById(ncc.getId()).orElseThrow().getDebt());
        receipts.cancel(r.getId(), "admin");
        eq("0", onHand(thep));
        eq("0", suppliers.findById(ncc.getId()).orElseThrow().getDebt());
        assertThrows(com.shop.common.ApiException.class, () -> receipts.cancel(r.getId(), "admin"), "khong huy 2 lan");
        assertLedgerMatches(thep);
    }

    @Test
    void openingStockCount_setsQtyAndCost() {
        Long id = counts.create(new StockCountController.CreateRequest("Ton dau ky", null), admin).get("id");
        StockCount c = counts.get(id);
        var line = c.getLines().stream().filter(l -> l.getProductId().equals(thep.getId())).findFirst().orElseThrow();
        counts.saveLines(id, List.of(new StockCountController.LineUpdate(line.getId(), new BigDecimal("50"), new BigDecimal("110000"))));
        StockCount posted = counts.post(id, admin);
        assertTrue(posted.getCode().matches("KK\\d{6}"));
        assertFalse(counts.get(id).getLines().isEmpty(), "xem lai phieu da ghi so van co dong");
        eq("50", onHand(thep));
        eq("110000", avg(thep));

        sell(thep, "8", 0);                          // ban trong luc dem lai
        Long id2 = counts.create(new StockCountController.CreateRequest("Kiem ke thang", null), admin).get("id");
        var line2 = counts.get(id2).getLines().stream().filter(l -> l.getProductId().equals(thep.getId())).findFirst().orElseThrow();
        counts.saveLines(id2, List.of(new StockCountController.LineUpdate(line2.getId(), new BigDecimal("40"), null)));
        counts.post(id2, admin);                     // so sach 42, dem 40 -> hao 2
        eq("40", onHand(thep));
        eq("110000", avg(thep));
        assertLedgerMatches(thep);
    }

    /** 2 quay cung chot 1 mat hang cung luc: khong mat dong so kho nao (khoa SELECT FOR UPDATE). */
    @Test
    void concurrentCheckouts_noLostUpdate() throws Exception {
        receive(thep, "100", "100000", "10000000");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Callable<Order>> jobs = java.util.stream.IntStream.range(0, 8).mapToObj(i -> (Callable<Order>) () -> sell(thep, "3", 0)).toList();
        for (Future<Order> f : pool.invokeAll(jobs)) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        eq("76", onHand(thep));                      // 100 - 8 x 3
        assertLedgerMatches(thep);
    }

    @Test
    void hangM2_batBuocSoTam_kieuHeSoKhongAnhHuongKho() {
        Product nhua = product("Nhua test " + System.nanoTime(), "m2", "90000");
        receive(nhua, "200", "50000", "0");                  // 200 met tam nhua
        Order o = orders.createDraft("nv");
        var ex = assertThrows(com.shop.common.ApiException.class,
                () -> orders.addItem(o.getId(), new OrderService.ItemRequest(nhua.getId(), new BigDecimal("10"), 0, null, null)));
        assertTrue(ex.getMessage().contains("số tấm"), ex.getMessage());
        // 4m x 19 tam, kho tam 0,4 -> tinh tien 30,4 m2; kho tru 76 m
        orders.addItem(o.getId(), new OrderService.ItemRequest(nhua.getId(), new BigDecimal("4"), 19, new BigDecimal("0.4"), null));
        orders.checkout(o.getId(), new OrderService.CheckoutRequest(null, "Khach le", null, null, null, new BigDecimal("999999999"), null));
        eq("30.4", orders.get(o.getId()).getItems().get(0).getTotalQuantity());
        eq("124", onHand(nhua));
        assertLedgerMatches(nhua);
        // hang khong co so tam (cay): tru dung SL
        Order o2 = sell(thep, "7", 0);
        eq("-7", onHand(thep));
    }
}

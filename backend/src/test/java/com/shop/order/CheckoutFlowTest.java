package com.shop.order;

import com.shop.common.ApiException;
import com.shop.customer.Customer;
import com.shop.customer.CustomerRepository;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CheckoutFlowTest {
    @Autowired OrderService service;
    @Autowired ProductRepository products;
    @Autowired CustomerRepository customers;
    @Autowired DebtLedgerRepository ledger;
    @Autowired DebtService debts;
    @Autowired com.shop.customer.CustomerController customerController;
    @Autowired com.shop.customer.DebtStatementService statements;

    Product product;
    Customer customer;

    @BeforeEach
    void setup() {
        Product p = new Product();
        p.setName("Ton lanh");
        p.setUnit("m");
        p.setPrice(new BigDecimal("1000"));
        product = products.save(p);
        Customer c = new Customer();
        c.setName("Khach A");
        customer = customers.save(c);
    }

    private Order draftWithItem() {
        Order o = service.createDraft("nv1");
        return service.addItem(o.getId(), new OrderService.ItemRequest(product.getId(), new BigDecimal("3"), 10, null, null));
    }

    @Test
    void draft_hasNoCode_untilCheckout() {
        Order o = draftWithItem();
        assertNull(o.getCode(), "Hoa don nhap chua co ma");
        Order paid = service.checkout(o.getId(), new OrderService.CheckoutRequest(
                null, "Khach le", null, null, null, new BigDecimal("40000"), null));
        assertTrue(paid.getCode().matches("HD\\d{8}"), paid.getCode());
    }

    @Test
    void checkout_withoutCustomerName_keepsDraftWithoutCode() {
        Order o = draftWithItem();
        assertThrows(com.shop.common.ApiException.class, () -> service.checkout(o.getId(),
                new OrderService.CheckoutRequest(null, " ", null, null, null, BigDecimal.ZERO, null)));
        assertNull(service.get(o.getId()).getCode());
    }

    @Test
    void checkout_creditCustomer_updatesDebtAndLedger() {
        Order o = draftWithItem(); // 3 x 10 x 1.08 x 1000 = 32400
        assertEquals(0, new BigDecimal("32400").compareTo(o.getItemsTotal()));
        assertEquals(0, new BigDecimal("32.4").compareTo(o.getItems().get(0).getTotalQuantity()));

        Order paid = service.checkout(o.getId(), new OrderService.CheckoutRequest(
                customer.getId(), null, null, null, new BigDecimal("100"), new BigDecimal("30000"), null));

        assertEquals(Order.Status.PAID, paid.getStatus());
        assertEquals(0, new BigDecimal("32500").compareTo(paid.getTotalPayable())); // 32400 + ship 100 + no cu 0
        assertEquals(0, new BigDecimal("2500").compareTo(paid.getDebtAfter()));
        Customer c = customers.findById(customer.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("2500").compareTo(c.getDebt()));
        assertEquals(2, ledger.findByCustomerIdOrderByIdDesc(c.getId()).size());
    }

    @Test
    void checkout_nextOrder_carriesOldDebt() {
        Order first = draftWithItem();
        service.checkout(first.getId(), new OrderService.CheckoutRequest(
                customer.getId(), null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, null)); // no 32400

        Order second = draftWithItem();
        Order paid = service.checkout(second.getId(), new OrderService.CheckoutRequest(
                customer.getId(), null, null, null, BigDecimal.ZERO, new BigDecimal("10000"), null));
        assertEquals(0, new BigDecimal("32400").compareTo(paid.getPreviousDebt()));
        assertEquals(0, new BigDecimal("54800").compareTo(paid.getDebtAfter())); // 32400+32400-10000
    }

    @Test
    void cancel_restoresDebt() {
        Order o = draftWithItem();
        service.checkout(o.getId(), new OrderService.CheckoutRequest(
                customer.getId(), null, null, null, BigDecimal.ZERO, new BigDecimal("12400"), null)); // no 20000
        assertEquals(0, new BigDecimal("20000").compareTo(customers.findById(customer.getId()).orElseThrow().getDebt()));

        Order cancelled = service.cancel(o.getId());
        assertEquals(Order.Status.CANCELLED, cancelled.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(customers.findById(customer.getId()).orElseThrow().getDebt()));
    }

    @Test
    void walkInCustomer_needsName_mustPayInFull() {
        Order o = draftWithItem(); // 32400
        assertThrows(ApiException.class, () -> service.checkout(o.getId(),
                new OrderService.CheckoutRequest(null, " ", null, null, BigDecimal.ZERO, new BigDecimal("32400"), null)));
        // tra thieu (ke ca thieu 1 dong, ke ca thieu phi ship) -> khong chot, van la hoa don nhap
        assertThrows(ApiException.class, () -> service.checkout(o.getId(),
                new OrderService.CheckoutRequest(null, "Anh Ba", null, null, BigDecimal.ZERO, new BigDecimal("32399"), null)));
        assertThrows(ApiException.class, () -> service.checkout(o.getId(),
                new OrderService.CheckoutRequest(null, "Anh Ba", null, null, new BigDecimal("10000"), new BigDecimal("32400"), null)));
        assertEquals(Order.Status.DRAFT, service.get(o.getId()).getStatus());

        Order paid = service.checkout(o.getId(),
                new OrderService.CheckoutRequest(null, "Anh Ba", null, null, new BigDecimal("10000"), new BigDecimal("42400"), null));
        assertEquals(0, BigDecimal.ZERO.compareTo(paid.getDebtAfter()));
        assertNull(paid.getCustomerId());
    }

    @Test
    void overpay_remainIsZero_paidStoredAsEntered_debtNotNegative() {
        Order o = draftWithItem(); // 32400
        Order paid = service.checkout(o.getId(), new OrderService.CheckoutRequest(
                customer.getId(), null, null, null, BigDecimal.ZERO, new BigDecimal("40000"), null));
        assertEquals(0, new BigDecimal("40000").compareTo(paid.getPaidAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(paid.getDebtAfter()));
        assertEquals(0, BigDecimal.ZERO.compareTo(customers.findById(customer.getId()).orElseThrow().getDebt()));
        // huy -> no quay ve nhu truoc hoa don
        service.cancel(o.getId());
        assertEquals(0, BigDecimal.ZERO.compareTo(customers.findById(customer.getId()).orElseThrow().getDebt()));
    }

    /** Vi du trong de xuat bien lai: moi dong cong tru khop, tra het no van in duoc phieu day du. */
    @Test
    void receipt_showsPurchasesBetweenPayments_andFullPayoff() {
        debts.setDebt(customer.getId(), new BigDecimal("1000000"), "No ban dau");
        var first = debts.pay(customer.getId(), new BigDecimal("300000"), null, com.shop.customer.DebtLedger.Method.CASH);
        Order o = draftWithItem(); // 32400
        service.checkout(o.getId(), new OrderService.CheckoutRequest(
                customer.getId(), null, null, null, BigDecimal.ZERO, new BigDecimal("100000"), null));
        var last = debts.pay(customer.getId(), new BigDecimal("632400"), null, com.shop.customer.DebtLedger.Method.TRANSFER);

        var doc = statements.receipt(last.getId());
        var lines = doc.lines();
        assertEquals(5, lines.size());
        assertEquals("Điều chỉnh: No ban dau", lines.get(0).description());
        assertTrue(lines.get(1).description().startsWith("Thu nợ PT"));
        assertTrue(lines.get(2).description().startsWith("Mua HĐ HD"));
        assertTrue(lines.get(3).description().startsWith("Trả khi mua HĐ HD"));
        assertTrue(lines.get(4).description().endsWith("(chuyển khoản)"));
        assertTrue(lines.get(4).highlight());
        long[] expect = {1000000, 700000, 732400, 632400, 0};
        for (int i = 0; i < 5; i++) assertEquals(0, BigDecimal.valueOf(expect[i]).compareTo(lines.get(i).balance()), "dong " + i);
        assertEquals(0, new BigDecimal("632400").compareTo(doc.totals().get(0).amount())); // no truoc khi thu
        assertEquals(0, new BigDecimal("632400").compareTo(doc.totals().get(1).amount())); // thu
        assertEquals(0, BigDecimal.ZERO.compareTo(doc.totals().get(2).amount()));           // con no

        // phieu lan thu dau: 2 dong
        assertEquals(2, statements.receipt(first.getId()).lines().size());
    }

    @Test
    void paidOrder_cannotBeEdited() {
        Order o = draftWithItem();
        service.checkout(o.getId(), new OrderService.CheckoutRequest(
                null, "Khach le", null, null, BigDecimal.ZERO, new BigDecimal("32400"), null));
        assertThrows(ApiException.class, () -> service.addItem(o.getId(),
                new OrderService.ItemRequest(product.getId(), BigDecimal.ONE, 0, null, null)));
    }

    @Test
    void debtPayment_cannotExceedDebt() {
        Order o = draftWithItem();
        service.checkout(o.getId(), new OrderService.CheckoutRequest(
                customer.getId(), null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, null)); // no 32400
        debts.pay(customer.getId(), new BigDecimal("400"), null);
        assertEquals(0, new BigDecimal("32000").compareTo(customers.findById(customer.getId()).orElseThrow().getDebt()));
        assertThrows(ApiException.class, () -> debts.pay(customer.getId(), new BigDecimal("99999"), null));
    }
}

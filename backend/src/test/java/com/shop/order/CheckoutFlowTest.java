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
    void walkInCustomer_needsName_canPayPartially_likePayDialog() {
        Order o = draftWithItem(); // 32400
        assertThrows(ApiException.class, () -> service.checkout(o.getId(),
                new OrderService.CheckoutRequest(null, " ", null, null, BigDecimal.ZERO, new BigDecimal("100"), null)));
        assertEquals(Order.Status.DRAFT, service.get(o.getId()).getStatus()); // loi -> rollback, van la nhap

        Order paid = service.checkout(o.getId(),
                new OrderService.CheckoutRequest(null, "Anh Ba", null, null, BigDecimal.ZERO, new BigDecimal("30000"), null));
        assertEquals(0, new BigDecimal("2400").compareTo(paid.getDebtAfter())); // ghi tren hoa don, khong vao cong no
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

    @Test
    void manualDebt_thenPayments_receiptShowsCurrentCycle() {
        debts.setDebt(customer.getId(), new BigDecimal("1000000"), "No ban dau");
        debts.pay(customer.getId(), new BigDecimal("300000"), null);
        Order o = draftWithItem(); // 32400
        service.checkout(o.getId(), new OrderService.CheckoutRequest(
                customer.getId(), null, null, null, BigDecimal.ZERO, new BigDecimal("100000"), null));

        var rows = customerController.receipt(customer.getId()).rows();
        assertEquals(2, rows.size());
        // lan 1 (ThanhToanNo): no cu 1.000.000, tra 300.000, con 700.000
        assertEquals(0, new BigDecimal("1000000").compareTo(rows.get(0).oldDebt()));
        assertEquals(0, new BigDecimal("700000").compareTo(rows.get(0).remainingDebt()));
        // lan 2 (luc chot hoa don): "No cu" = no truoc hoa don 700.000, tra 100.000, con 632.400
        assertEquals(0, new BigDecimal("700000").compareTo(rows.get(1).oldDebt()));
        assertEquals(0, new BigDecimal("100000").compareTo(rows.get(1).amount()));
        assertEquals(0, new BigDecimal("632400").compareTo(rows.get(1).remainingDebt()));

        // tra het -> chu ky ket thuc, bien lai rong (app cu: "Khong co ban ghi thanh toan nao")
        debts.pay(customer.getId(), new BigDecimal("632400"), null);
        assertTrue(customerController.receipt(customer.getId()).rows().isEmpty());
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

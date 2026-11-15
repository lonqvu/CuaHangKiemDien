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
    void checkout_creditCustomer_updatesDebtAndLedger() {
        Order o = draftWithItem(); // 3 x 10 x 1.08 x 1000 = 32400
        assertEquals(0, new BigDecimal("32400").compareTo(o.getItemsTotal()));

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
    void walkInCustomer_mustPayInFull() {
        Order o = draftWithItem();
        assertThrows(ApiException.class, () -> service.checkout(o.getId(),
                new OrderService.CheckoutRequest(null, "Khach le", null, null, BigDecimal.ZERO, new BigDecimal("100"), null)));
        // loi -> don van la DRAFT
        assertEquals(Order.Status.DRAFT, service.get(o.getId()).getStatus());
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

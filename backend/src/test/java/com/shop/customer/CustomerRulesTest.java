package com.shop.customer;

import com.shop.common.ApiException;
import com.shop.order.OrderService;
import com.shop.product.Product;
import com.shop.product.ProductRepository;
import com.shop.report.ReportController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CustomerRulesTest {
    @Autowired CustomerController customers;
    @Autowired DebtService debts;
    @Autowired OrderService orders;
    @Autowired ProductRepository products;
    @Autowired ReportController reports;

    private Customer create(String name) {
        return customers.create(new CustomerController.CustomerRequest(name, null, null, null),
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin", null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Test
    void renameToExistingName_conflict() {
        String a = "Khách A " + UUID.randomUUID();
        create(a);
        Customer b = create("Khách B " + UUID.randomUUID());
        var ex = assertThrows(ApiException.class, () -> customers.update(b.getId(), new CustomerController.CustomerRequest(a.toUpperCase(), null, null, null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        customers.update(b.getId(), new CustomerController.CustomerRequest(b.getName(), null, "0912", null)); // giu ten minh: duoc
    }

    @Test
    void hideCustomerWithDebt_blocked_untilDebtCleared() {
        Customer c = create("Khách nợ " + UUID.randomUUID());
        debts.setDebt(c.getId(), new BigDecimal("500000"), "Nợ cũ");
        var ex = assertThrows(ApiException.class, () -> customers.delete(c.getId()));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        debts.setDebt(c.getId(), BigDecimal.ZERO, "Nợ khó đòi, xóa nợ");
        customers.delete(c.getId());
    }

    /** Tong doanh thu cac loai hang = tong doanh thu (hang chua phan loai gom vao "Chua phan loai"). */
    @Test
    void categoryReport_includesUncategorized_sumsToTotal() {
        Product p = new Product();
        p.setName("Hàng chưa phân loại " + UUID.randomUUID()); p.setUnit("cái"); p.setPrice(new BigDecimal("12345"));
        p = products.save(p);
        var o = orders.createDraft("thu");
        orders.addItem(o.getId(), new OrderService.ItemRequest(p.getId(), new BigDecimal("2"), 0, null, null));
        orders.checkout(o.getId(), new OrderService.CheckoutRequest(null, "Khách lẻ", null, null, BigDecimal.ZERO, new BigDecimal("24690"), null));
        LocalDate d = LocalDate.now();
        var cats = reports.categories(d, d);
        BigDecimal total = reports.revenue(d, d).stream().map(ReportController.DayRevenue::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, total.compareTo(cats.stream().map(ReportController.CategoryStat::revenue).reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertTrue(cats.stream().anyMatch(c -> c.category().equals("Chưa phân loại") && c.revenue().compareTo(new BigDecimal("24690")) >= 0));
    }
}

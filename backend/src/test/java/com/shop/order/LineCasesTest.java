package com.shop.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.ApiException;
import com.shop.product.Product;
import com.shop.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bo case tinh tien dung chung voi frontend (scripts/gen-line-cases.py, ket qua tinh doc lap bang Decimal cua Python).
 * Frontend chay cung bo case nay trong frontend/tests/calc.test.ts -> man hinh xem truoc va server luon ra cung so tien.
 */
@SpringBootTest
@ActiveProfiles("test")
class LineCasesTest {
    record Case(String name, String quantity, int sheets, String factor, String unitPrice,
                String expectFactor, String expectTotalQuantity, String expectLineTotal, boolean reject) {}

    @Autowired OrderService service;
    @Autowired ProductRepository products;

    static List<Case> cases() throws Exception {
        try (InputStream in = LineCasesTest.class.getResourceAsStream("/line-cases.json")) {
            return List.of(new ObjectMapper().readValue(in, Case[].class));
        }
    }

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void lineCalculator_matchesIndependentDecimalResults() throws Exception {
        List<String> fails = new ArrayList<>();
        for (Case c : cases()) {
            BigDecimal f = LineCalculator.normalizeFactor(c.sheets(), d(c.factor()));
            BigDecimal raw = LineCalculator.totalQuantity(d(c.quantity()), c.sheets(), f);
            BigDecimal stored = LineCalculator.storedQuantity(raw);
            BigDecimal total = LineCalculator.lineTotal(d(c.unitPrice()), raw);
            if (f.compareTo(d(c.expectFactor())) != 0 || stored.compareTo(d(c.expectTotalQuantity())) != 0
                    || total.compareTo(d(c.expectLineTotal())) != 0)
                fails.add(c.name() + ": he so " + f + " SL " + stored + " tien " + total);
        }
        assertTrue(fails.isEmpty(), "Lech: " + fails);
    }

    @Test
    void orderService_addsEveryCase_rejectsZeroQuantity_andSumsTotal() throws Exception {
        Product p = new Product();
        p.setName("Hang test line-cases");
        p.setUnit("m");
        p.setPrice(new BigDecimal("1000"));
        p = products.save(p);

        List<Case> all = cases();
        Order o = service.createDraft("test");
        BigDecimal expectedSum = BigDecimal.ZERO;
        int lines = 0;
        for (Case c : all) {
            var req = new OrderService.ItemRequest(p.getId(), d(c.quantity()), c.sheets(), d(c.factor()), d(c.unitPrice()));
            if (c.reject()) {
                Long id = o.getId();
                assertThrows(ApiException.class, () -> service.addItem(id, req), c.name());
                continue;
            }
            o = service.addItem(o.getId(), req);
            lines++;
            OrderItem it = o.getItems().get(o.getItems().size() - 1);
            assertEquals(0, d(c.expectTotalQuantity()).compareTo(it.getTotalQuantity()), c.name() + " Tong SL");
            assertEquals(0, d(c.expectLineTotal()).compareTo(it.getLineTotal()), c.name() + " Thanh tien");
            assertEquals(0, d(c.expectFactor()).compareTo(it.getFactor()), c.name() + " He so");
            expectedSum = expectedSum.add(d(c.expectLineTotal()));
        }
        Order saved = service.get(o.getId());
        assertEquals(lines, saved.getItems().size());
        assertEquals(0, expectedSum.compareTo(saved.getItemsTotal()), "Tien hang = tong thanh tien cac dong");
    }
}

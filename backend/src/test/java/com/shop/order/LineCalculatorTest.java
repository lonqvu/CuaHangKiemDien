package com.shop.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LineCalculatorTest {
    @Test
    void noSheetsNoFactor_usesQuantityAsIs() {
        BigDecimal q = LineCalculator.totalQuantity(new BigDecimal("2.5"), 0, BigDecimal.ZERO);
        assertEquals(new BigDecimal("2.5"), q);
    }

    @Test
    void sheetsDefaultFactor108() {
        BigDecimal f = LineCalculator.normalizeFactor(10, BigDecimal.ZERO);
        assertEquals(new BigDecimal("1.08"), f);
        // 3 x 10 x 1.08 = 32.4 ; don gia 1000 -> 32400.00
        BigDecimal q = LineCalculator.totalQuantity(new BigDecimal("3"), 10, f);
        assertEquals(0, new BigDecimal("32.40").compareTo(q));
        assertEquals(new BigDecimal("32400.00"), LineCalculator.lineTotal(new BigDecimal("1000"), q));
    }
}

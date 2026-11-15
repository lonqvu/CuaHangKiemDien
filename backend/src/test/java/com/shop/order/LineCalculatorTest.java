package com.shop.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** So mong doi tinh theo ProductDialog.getTongSoLuong() + DecimalFormat cua app Swing cu. */
class LineCalculatorTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void noSheetsNoFactor_usesQuantityAsIs() {
        assertEquals(d("2.5"), LineCalculator.totalQuantity(d("2.5"), 0, BigDecimal.ZERO));
    }

    @Test
    void sheetsDefaultFactor108() {
        BigDecimal f = LineCalculator.normalizeFactor(10, BigDecimal.ZERO);
        assertEquals(d("1.08"), f);
        BigDecimal q = LineCalculator.totalQuantity(d("3"), 10, f); // 3 x 10 x 1.08 = 32.4
        assertEquals(0, d("32.4").compareTo(q));
        assertEquals(d("32400"), LineCalculator.lineTotal(d("1000"), q));
    }

    @Test
    void sheetsZeroButFactorSet_totalIsZero_likeOldApp() {
        // app cu: so tam = 0, he so != 0 -> SL x 0 x he so = 0
        assertEquals(0, BigDecimal.ZERO.compareTo(LineCalculator.totalQuantity(d("5"), 0, d("1.08"))));
    }

    @Test
    void lineTotal_roundsToWholeDong_halfEven() {
        // 333 x 1.08 = 359.64 -> 360 (DecimalFormat("###,###,###"))
        assertEquals(d("360"), LineCalculator.lineTotal(d("333"), LineCalculator.totalQuantity(d("1"), 1, d("1.08"))));
        // .5 lam tron ve so chan: 12.5 -> 12, 13.5 -> 14
        assertEquals(d("12"), LineCalculator.lineTotal(d("25"), d("0.5")));
        assertEquals(d("14"), LineCalculator.lineTotal(d("27"), d("0.5")));
    }

    @Test
    void money_usesUnroundedQuantity_butStoredQuantityHas3Decimals() {
        // 1.234 x 3 tam x 1.08 = 3.99816 -> luu 3.998; tien = 50000 x 3.99816 = 199908
        BigDecimal raw = LineCalculator.totalQuantity(d("1.234"), 3, d("1.08"));
        assertEquals(d("3.998"), LineCalculator.storedQuantity(raw));
        assertEquals(d("199908"), LineCalculator.lineTotal(d("50000"), raw));
    }
}

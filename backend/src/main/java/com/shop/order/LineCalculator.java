package com.shop.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cong thuc tinh so luong cua nghiep vu cu (ban theo tam):
 *   Tong SL = So luong x So tam x He so   (neu so tam va he so deu = 0 thi Tong SL = So luong)
 *   Thanh tien = Don gia x Tong SL
 * Chi tinh o day (backend); frontend chi hien thi.
 */
public final class LineCalculator {
    public static final BigDecimal DEFAULT_FACTOR = new BigDecimal("1.08");

    private LineCalculator() {}

    /** Neu co so tam ma khong nhap he so thi dung 1.08 (giong hanh vi man hinh cu). */
    public static BigDecimal normalizeFactor(int sheets, BigDecimal factor) {
        if (factor == null) factor = BigDecimal.ZERO;
        if (sheets > 0 && factor.signum() == 0) return DEFAULT_FACTOR;
        return factor;
    }

    public static BigDecimal totalQuantity(BigDecimal quantity, int sheets, BigDecimal factor) {
        if (sheets == 0 && factor.signum() == 0) return quantity;
        return quantity.multiply(BigDecimal.valueOf(sheets)).multiply(factor);
    }

    public static BigDecimal lineTotal(BigDecimal unitPrice, BigDecimal totalQuantity) {
        return unitPrice.multiply(totalQuantity).setScale(2, RoundingMode.HALF_UP);
    }
}

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

    /** Tong SL chua lam tron (app cu tinh tien tren so nay). */
    public static BigDecimal totalQuantity(BigDecimal quantity, int sheets, BigDecimal factor) {
        if (sheets == 0 && factor.signum() == 0) return quantity;
        return quantity.multiply(BigDecimal.valueOf(sheets)).multiply(factor);
    }

    /** Tong SL luu/hien thi: app cu dung DecimalFormat("#.###") -> 3 so le, HALF_EVEN. */
    public static BigDecimal storedQuantity(BigDecimal rawTotalQuantity) {
        return rawTotalQuantity.setScale(3, RoundingMode.HALF_EVEN);
    }

    /** Thanh tien: app cu dung DecimalFormat("###,###,###") -> lam tron ve dong, HALF_EVEN, tren Tong SL chua lam tron. */
    public static BigDecimal lineTotal(BigDecimal unitPrice, BigDecimal rawTotalQuantity) {
        return unitPrice.multiply(rawTotalQuantity).setScale(0, RoundingMode.HALF_EVEN);
    }
}

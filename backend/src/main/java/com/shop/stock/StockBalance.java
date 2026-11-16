package com.shop.stock;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** So du ton hien tai cua 1 san pham (don vi goc). Chi StockService duoc sua, cung transaction voi StockMovement. */
@Entity
@Table(name = "stock_balance")
@Getter @Setter
public class StockBalance {
    @Id
    @Column(name = "product_id")
    private Long productId;
    @Column(name = "on_hand", nullable = false, precision = 14, scale = 3)
    private BigDecimal onHand = BigDecimal.ZERO;
    /** Gia von binh quan di dong / don vi goc. 0 = chua biet (chua tung nhap). */
    @Column(name = "avg_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal avgCost = BigDecimal.ZERO;
    @Version
    private Long version;
}

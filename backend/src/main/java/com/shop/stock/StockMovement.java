package com.shop.stock;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Mot dong so kho (the kho). Chi them, khong sua/xoa: huy chung tu = ghi them dong nguoc. */
@Entity
@Table(name = "stock_movement")
@Getter @Setter
public class StockMovement {
    /** RECEIPT nhap hang, RECEIPT_CANCEL huy phieu nhap, SALE ban hang, SALE_CANCEL huy hoa don, COUNT kiem ke, ADJUST xuat huy/dieu chinh. */
    public enum Type { RECEIPT, RECEIPT_CANCEL, SALE, SALE_CANCEL, COUNT, ADJUST, SALE_REVISE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;
    /** Co dau: + nhap, - xuat. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal qty;
    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitCost;
    /** Gia tri = qty x unit_cost (co dau). */
    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal value;
    @Column(name = "balance_after", nullable = false, precision = 14, scale = 3)
    private BigDecimal balanceAfter;
    @Column(name = "avg_cost_after", nullable = false, precision = 18, scale = 4)
    private BigDecimal avgCostAfter;
    /** ORDER, RECEIPT, COUNT, ADJUST */
    @Column(name = "ref_type", nullable = false)
    private String refType;
    @Column(name = "ref_id")
    private Long refId;
    @Column(name = "ref_line_id")
    private Long refLineId;
    @Column(name = "ref_code")
    private String refCode;
    private String note;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

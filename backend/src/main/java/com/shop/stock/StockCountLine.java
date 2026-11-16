package com.shop.stock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "stock_count_line")
@Getter @Setter
public class StockCountLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "count_id")
    private StockCount count;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "product_name", nullable = false)
    private String productName;
    private String unit;
    /** Ton so sach luc ghi so (truoc do chi de xem). */
    @Column(name = "system_qty", precision = 14, scale = 3)
    private BigDecimal systemQty;
    /** null = chua dem, bo qua khi ghi so. */
    @Column(name = "counted_qty", precision = 14, scale = 3)
    private BigDecimal countedQty;
    @Column(precision = 14, scale = 3)
    private BigDecimal diff;
    /** Gia von cho phan thua (vd ton dau ky). null = theo gia von binh quan hien tai. */
    @Column(name = "unit_cost", precision = 18, scale = 4)
    private BigDecimal unitCost;
}

package com.shop.stock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "goods_receipt_line")
@Getter @Setter
public class GoodsReceiptLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id")
    private GoodsReceipt receipt;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "product_name", nullable = false)
    private String productName;
    /** Don vi goc (don vi ton kho). */
    private String unit;
    /** Don vi nhap thuc te (kg, bo, cuon...). */
    @Column(name = "input_unit")
    private String inputUnit;
    @Column(name = "input_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal inputQty;
    @Column(name = "input_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal inputPrice;
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal ratio;
    /** So luong theo don vi goc - mac dinh input_qty x ratio, sua duoc theo so do thuc te. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal qty;
    /** Gia von / don vi goc = line_total / qty. */
    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitCost;
    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal;
}

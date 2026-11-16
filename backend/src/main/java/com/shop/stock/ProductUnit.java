package com.shop.stock;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Don vi quy doi khi NHAP hang: 1 [unit] = ratio [don vi goc cua san pham].
 * Vd ton ban theo m, nhap theo kg: unit = kg, ratio = 0.318. Thep ban theo cay, nhap theo bo: unit = bo, ratio = 10.
 * Chi dung de goi y so luong luc nhap; so luong da quy doi duoc luu vao phieu/so kho, nen sua ty le sau nay khong lam sai so cu.
 */
@Entity
@Table(name = "product_unit")
@Getter @Setter
public class ProductUnit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(nullable = false)
    private String unit;
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal ratio;
}

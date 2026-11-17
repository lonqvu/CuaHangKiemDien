package com.shop.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter @Setter
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
    @Column(nullable = false)
    private String name;
    private String unit;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;
    private boolean active = true;
    /** Muc ton toi thieu (don vi goc): ton <= muc nay thi canh bao sap het. 0 = khong canh bao. */
    @Column(name = "min_stock", nullable = false, precision = 14, scale = 3)
    private BigDecimal minStock = BigDecimal.ZERO;
}

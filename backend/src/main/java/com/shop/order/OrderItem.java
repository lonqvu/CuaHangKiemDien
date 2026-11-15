package com.shop.order;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_item")
@Getter @Setter
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;
    @Column(name = "product_id")
    private Long productId;
    // Chup lai ten/don vi/gia luc ban, sua san pham sau nay khong lam doi hoa don cu
    @Column(name = "product_name", nullable = false)
    private String productName;
    private String unit;
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;
    @Column(nullable = false)
    private BigDecimal quantity;
    private int sheets;
    private BigDecimal factor = BigDecimal.ZERO;
    @Column(name = "total_quantity", nullable = false)
    private BigDecimal totalQuantity;
    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;
}

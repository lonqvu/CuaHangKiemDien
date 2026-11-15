package com.shop.order;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "SalesOrder")
@Table(name = "orders")
@Getter @Setter
public class Order {
    public enum Status { DRAFT, PAID, CANCELLED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;
    @Column(name = "customer_id")
    private Long customerId;
    @Column(name = "customer_name")
    private String customerName;
    @Column(name = "customer_address")
    private String customerAddress;
    @Column(name = "customer_phone")
    private String customerPhone;
    @Column(name = "staff_name")
    private String staffName;
    @Column(name = "items_total", nullable = false)
    private BigDecimal itemsTotal = BigDecimal.ZERO;
    @Column(name = "shipping_fee", nullable = false)
    private BigDecimal shippingFee = BigDecimal.ZERO;
    @Column(name = "previous_debt", nullable = false)
    private BigDecimal previousDebt = BigDecimal.ZERO;
    @Column(name = "total_payable", nullable = false)
    private BigDecimal totalPayable = BigDecimal.ZERO;
    @Column(name = "paid_amount", nullable = false)
    private BigDecimal paidAmount = BigDecimal.ZERO;
    @Column(name = "debt_after", nullable = false)
    private BigDecimal debtAfter = BigDecimal.ZERO;
    private String note;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "checked_out_at")
    private LocalDateTime checkedOutAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();
}

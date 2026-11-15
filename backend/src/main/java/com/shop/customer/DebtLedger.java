package com.shop.customer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "debt_ledger")
@Getter @Setter
public class DebtLedger {
    public enum Type { ORDER, PAYMENT, ADJUST }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "customer_id", nullable = false)
    private Long customerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;
    /** Co dau: duong = no tang, am = no giam. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;
    @Column(name = "order_id")
    private Long orderId;
    private String note;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

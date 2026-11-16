package com.shop.supplier;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** So cong no nha cung cap (giong debt_ledger cua khach): moi lan no tang/giam la 1 dong. */
@Entity
@Table(name = "supplier_ledger")
@Getter @Setter
public class SupplierLedger {
    /** RECEIPT nhap chiu, PAYMENT tra NCC, CANCEL huy phieu nhap, MANUAL no ban dau / sua tay. */
    public enum Type { RECEIPT, PAYMENT, CANCEL, MANUAL }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;
    /** Co dau: duong = no NCC tang. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;
    @Column(name = "receipt_id")
    private Long receiptId;
    private String note;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

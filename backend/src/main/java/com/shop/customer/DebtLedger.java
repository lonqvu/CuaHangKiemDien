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
    /**
     * ORDER: mua hang, PAYMENT: tra no (luc mua hoac thu no), ADJUST: huy hoa don,
     * MANUAL: nhap no ban dau / dieu chinh no (co ly do), VOID: huy phieu thu (dao nguoc mot PAYMENT).
     */
    public enum Type { ORDER, PAYMENT, ADJUST, MANUAL, VOID }

    /** Hinh thuc thu tien. */
    public enum Method { CASH, TRANSFER }

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
    /** So phieu thu (PT000001) - chi lan thu no o man Khach hang; tra luc mua hang da co hoa don. */
    @Column(name = "receipt_no")
    private String receiptNo;
    @Enumerated(EnumType.STRING)
    private Method method;
    /** Nguoi thao tac (nguoi thu tien, nguoi sua no...). */
    @Column(name = "created_by")
    private String createdBy;
    /** Dong VOID: id dong PAYMENT bi huy. */
    @Column(name = "reversal_of")
    private Long reversalOf;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

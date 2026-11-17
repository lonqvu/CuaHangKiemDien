package com.shop.stock;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Phieu nhap hang. DRAFT sua tu do; POSTED da vao kho + cong no NCC (khong sua, chi huy); CANCELLED da ghi nguoc. */
@Entity
@Table(name = "goods_receipt")
@Getter @Setter
public class GoodsReceipt {
    public enum Status { DRAFT, POSTED, CANCELLED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** PNxxxxxx, cap khi ghi so (so phieu lien tuc). */
    @Column(unique = true)
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;
    @Column(name = "supplier_id")
    private Long supplierId;
    @Column(name = "supplier_name")
    private String supplierName;
    private String note;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;
    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "posted_at")
    private LocalDateTime postedAt;
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @org.hibernate.annotations.BatchSize(size = 100) // danh sach phieu dem so dong: 1 cau truy van cho ca trang, khong N+1
    private List<GoodsReceiptLine> lines = new ArrayList<>();
}

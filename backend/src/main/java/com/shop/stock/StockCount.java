package com.shop.stock;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Phieu kiem ke: dem thuc te, ghi so phan chenh lech. Phieu dau tien = ton dau ky (kem gia von ban dau). */
@Entity
@Table(name = "stock_count")
@Getter @Setter
public class StockCount {
    public enum Status { DRAFT, POSTED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;
    private String note;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @OneToMany(mappedBy = "count", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productName")
    @org.hibernate.annotations.BatchSize(size = 100)
    private List<StockCountLine> lines = new ArrayList<>();
}

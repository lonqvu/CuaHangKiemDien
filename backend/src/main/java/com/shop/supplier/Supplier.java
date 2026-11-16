package com.shop.supplier;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
public class Supplier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    private String phone;
    private String address;
    /** So tien cua hang con no nha cung cap. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal debt = BigDecimal.ZERO;
    private boolean active = true;
    /** Khoa lac quan nhu Customer: 2 may cung sua cong no NCC thi may sau bao loi. */
    @Version
    private Long version;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

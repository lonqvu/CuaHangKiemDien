package com.shop.customer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter @Setter
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    private String address;
    private String phone;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal debt = BigDecimal.ZERO;
    private boolean active = true;
    /** Khoa lac quan: 2 may cung sua cong no mot khach thi may den sau bi bao loi, khong ghi de. */
    @Version
    private Long version;
}

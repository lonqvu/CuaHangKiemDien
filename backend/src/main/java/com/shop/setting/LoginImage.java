package com.shop.setting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Anh nen man dang nhap - chi 1 dong (id = 1). */
@Entity
@Table(name = "login_image")
@Getter @Setter
public class LoginImage {
    public static final short ID = 1;

    @Id
    private Short id = ID;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(nullable = false)
    private byte[] data;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "updated_by")
    private String updatedBy;
}

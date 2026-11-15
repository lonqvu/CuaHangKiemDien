package com.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShopApplication {
    public static void main(String[] args) {
        // Gio Viet Nam cho moi thoi diem luu va in (ngay gio tren hoa don), ke ca khi server chay UTC
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(
                System.getenv().getOrDefault("APP_TZ", "Asia/Ho_Chi_Minh")));
        System.setProperty("java.awt.headless", "true"); // Jasper do chu bang AWT, khong can man hinh
        SpringApplication.run(ShopApplication.class, args);
    }
}

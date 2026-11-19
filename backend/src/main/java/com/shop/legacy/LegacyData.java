package com.shop.legacy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * File du-lieu-cu.json (tools/chuyen-du-lieu/xuat.cjs doc tu SQL Server cua phan mem cu).
 * So tien / so luong la CHU (vd "603500.0000") de khong sai le; ngay gio "yyyy-MM-dd HH:mm:ss.SSS" (gio VN).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LegacyData(String nguon, String xuatLuc, List<Classify> classify, List<Product> products,
                         List<Customer> customers, List<Order> orders, List<Bill> bills, Integer invoiceCounter) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Classify(Integer id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(Integer id, Integer classifyId, String name, String price, String unit, String createdAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(Integer id, String name, String address, String phone, String debt, String createdAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(String id, String customer, String address, String phone, String date, Integer customerId,
                        String totalBill, String oldDebt, String totalOrder, String pay, String debtBack, String ship,
                        String staff, String note) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bill(Integer id, String orderId, Integer productId, String productName, String unit, String amount,
                       String price, String intoMoney, String heSo, Integer soTam, String tongSoLuong) {}
}

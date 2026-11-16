package com.shop.order;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) { this.service = service; }

    public record ItemDto(Long id, Long productId, String productName, String unit, BigDecimal unitPrice,
                          BigDecimal quantity, int sheets, BigDecimal factor, BigDecimal totalQuantity, BigDecimal lineTotal) {}
    public record OrderDto(Long id, String code, Order.Status status, Long customerId, String customerName,
                           String customerAddress, String customerPhone, String staffName,
                           BigDecimal itemsTotal, BigDecimal shippingFee, BigDecimal previousDebt,
                           BigDecimal totalPayable, BigDecimal paidAmount, BigDecimal debtAfter, BigDecimal refundAmount, String note,
                           LocalDateTime createdAt, LocalDateTime checkedOutAt, List<ItemDto> items) {}

    private OrderDto dto(Order o) {
        return new OrderDto(o.getId(), o.getCode(), o.getStatus(), o.getCustomerId(), o.getCustomerName(),
                o.getCustomerAddress(), o.getCustomerPhone(), o.getStaffName(),
                o.getItemsTotal(), o.getShippingFee(), o.getPreviousDebt(), o.getTotalPayable(),
                o.getPaidAmount(), o.getDebtAfter(), o.getRefundAmount(), o.getNote(), o.getCreatedAt(), o.getCheckedOutAt(),
                o.getItems().stream().map(i -> new ItemDto(i.getId(), i.getProductId(), i.getProductName(), i.getUnit(),
                        i.getUnitPrice(), i.getQuantity(), i.getSheets(), i.getFactor(),
                        i.getTotalQuantity(), i.getLineTotal())).toList());
    }

    @PostMapping
    public OrderDto create(Authentication auth) {
        String name = auth.getDetails() instanceof String s ? s : auth.getName();
        return dto(service.createDraft(name));
    }

    @GetMapping
    public List<OrderDto> list(@RequestParam(defaultValue = "PAID") Order.Status status,
                               @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
                               @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return service.list(status, from, to).stream().map(this::dto).toList();
    }

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable Long id) { return dto(service.get(id)); }

    @PostMapping("/{id}/items")
    public OrderDto addItem(@PathVariable Long id, @RequestBody OrderService.ItemRequest r) {
        return dto(service.addItem(id, r));
    }

    @PutMapping("/{id}/items/{itemId}")
    public OrderDto updateItem(@PathVariable Long id, @PathVariable Long itemId, @RequestBody OrderService.ItemRequest r) {
        return dto(service.updateItem(id, itemId, r));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public OrderDto removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        return dto(service.removeItem(id, itemId));
    }

    @DeleteMapping("/{id}")
    public void deleteDraft(@PathVariable Long id) { service.deleteDraft(id); }

    @PostMapping("/{id}/checkout")
    public OrderDto checkout(@PathVariable Long id, @RequestBody OrderService.CheckoutRequest r) {
        return dto(service.checkout(id, r));
    }

    /** Xem truoc khi huy: no duoc tru bao nhieu, cua hang phai tra lai khach bao nhieu tien mat. */
    @GetMapping("/{id}/cancel-preview")
    public OrderService.CancelEffect cancelPreview(@PathVariable Long id) { return service.cancelPreview(id); }

    @PostMapping("/{id}/cancel")
    public OrderDto cancel(@PathVariable Long id) { return dto(service.cancel(id)); }
}

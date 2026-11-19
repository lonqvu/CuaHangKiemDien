package com.shop.order;

import com.shop.common.PageDto;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;
    private final OrderRevisionService revisions;

    public OrderController(OrderService service, OrderRevisionService revisions) { this.service = service; this.revisions = revisions; }

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

    /** Dong trong danh sach: khong kem dong hang (nhe, khong N+1). Chi tiet: GET /{id}. */
    public record OrderRow(Long id, String code, Order.Status status, Long customerId, String customerName,
                           String customerPhone, String staffName, BigDecimal itemsTotal, BigDecimal totalPayable,
                           BigDecimal paidAmount, BigDecimal debtAfter, LocalDateTime createdAt, LocalDateTime checkedOutAt) {}

    private static OrderRow row(Order o) {
        return new OrderRow(o.getId(), o.getCode(), o.getStatus(), o.getCustomerId(), o.getCustomerName(), o.getCustomerPhone(),
                o.getStaffName(), o.getItemsTotal(), o.getTotalPayable(), o.getPaidAmount(), o.getDebtAfter(),
                o.getCreatedAt(), o.getCheckedOutAt());
    }

    private static final Map<String, String> SORTABLE = Map.of(
            "code", "code", "customerName", "customerName", "itemsTotal", "itemsTotal", "totalPayable", "totalPayable",
            "paidAmount", "paidAmount", "debtAfter", "debtAfter", "date", "checkedOutAt", "createdAt", "createdAt");

    /**
     * Danh sach hoa don co phan trang. status: PAID | CANCELLED | DRAFT, nhieu gia tri cach nhau dau phay
     * (mac dinh PAID,CANCELLED). summary: count, itemsTotal, collected, debtAfter, countByStatus.
     */
    @GetMapping
    public PageDto<OrderRow> list(@RequestParam(defaultValue = "PAID,CANCELLED") List<Order.Status> status,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(defaultValue = "") String q,
                                  @RequestParam(required = false) Long customerId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(defaultValue = "") String sort) {
        boolean drafts = status.size() == 1 && status.get(0) == Order.Status.DRAFT;
        Sort fallback = drafts ? Sort.by(Sort.Direction.DESC, "createdAt", "id") : Sort.by(Sort.Direction.DESC, "checkedOutAt", "id");
        Map<String, String> allowed = new java.util.HashMap<>(SORTABLE);
        if (drafts) allowed.put("date", "createdAt");
        var filter = new OrderService.Filter(status, from, to, q, customerId);
        var p = service.page(filter, PageDto.pageable(page, size, sort, allowed, fallback));
        var s = service.summary(filter);
        return PageDto.of(p, OrderController::row, Map.of("count", s.count(), "itemsTotal", s.itemsTotal(),
                "collected", s.collected(), "debtAfter", s.debtAfter(), "countByStatus", s.countByStatus()));
    }

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable Long id) { return dto(service.get(id)); }

    @PostMapping("/{id}/items")
    public OrderDto addItem(@PathVariable Long id, @RequestBody OrderService.ItemRequest r) {
        return dto(service.addItem(id, r));
    }

    @PostMapping("/{id}/items/batch")
    public OrderDto addItems(@PathVariable Long id, @RequestBody java.util.List<OrderService.ItemRequest> rs) {
        return dto(service.addItems(id, rs));
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

    /** Sua hoa don da chot (chi quan tri - SecurityConfig): xem truoc tien / no thay doi, chua luu. */
    @PostMapping("/{id}/revise-preview")
    public OrderRevisionService.Effect revisePreview(@PathVariable Long id, @RequestBody OrderRevisionService.ReviseRequest r) {
        return revisions.preview(id, r);
    }

    /** Sua hoa don da chot: tinh lai tien, cong no, kho; luu lich su. */
    @PostMapping("/{id}/revise")
    public OrderDto revise(@PathVariable Long id, @RequestBody OrderRevisionService.ReviseRequest r) {
        return dto(revisions.revise(id, r));
    }

    /** Lich su sua hoa don (moi nhat truoc). */
    @GetMapping("/{id}/revisions")
    public List<OrderRevisionService.Revision> revisionHistory(@PathVariable Long id) { return revisions.history(id); }
}

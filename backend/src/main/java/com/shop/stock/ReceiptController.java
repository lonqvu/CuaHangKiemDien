package com.shop.stock;

import com.shop.common.PageDto;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Phieu nhap hang. Chi ADMIN. */
@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {
    private final ReceiptService service;
    private final GoodsReceiptRepository receipts;

    public ReceiptController(ReceiptService service, GoodsReceiptRepository receipts) {
        this.service = service; this.receipts = receipts;
    }

    static String who(Authentication a) { return a.getDetails() instanceof String s ? s : a.getName(); }

    /** Dong trong danh sach (khong kem dong hang). */
    public record ReceiptRow(Long id, String code, GoodsReceipt.Status status, Long supplierId, String supplierName,
                             BigDecimal total, BigDecimal paidAmount, int lineCount, String createdBy,
                             LocalDateTime createdAt, LocalDateTime postedAt) {}

    private static final Map<String, String> SORTABLE = Map.of("code", "code", "date", "postedAt", "total", "total",
            "supplierName", "supplierName", "createdAt", "createdAt");

    @GetMapping
    @Transactional(readOnly = true)
    public PageDto<ReceiptRow> list(@RequestParam(defaultValue = "POSTED") List<GoodsReceipt.Status> status,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(defaultValue = "") String q,
                                    @RequestParam(required = false) Long supplierId,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(defaultValue = "") String sort) {
        LocalDateTime f = from == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : from.atStartOfDay();
        LocalDateTime t = to == null ? LocalDateTime.of(9999, 1, 1, 0, 0) : to.plusDays(1).atStartOfDay();
        String like = PageDto.like(q);
        long sup = supplierId == null ? 0 : supplierId;
        boolean drafts = status.size() == 1 && status.get(0) == GoodsReceipt.Status.DRAFT;
        Map<String, String> allowed = new java.util.HashMap<>(SORTABLE);
        if (drafts) allowed.put("date", "createdAt");
        Sort fallback = Sort.by(Sort.Direction.DESC, drafts ? "createdAt" : "postedAt", "id");
        var p = receipts.page(status, f, t, like, sup, PageDto.pageable(page, size, sort, allowed, fallback));
        Object[] s = receipts.summary(status, f, t, like, sup).get(0);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (GoodsReceipt.Status st : GoodsReceipt.Status.values()) byStatus.put(st.name(), 0L);
        for (Object[] r : receipts.countByStatus(f, t, like, sup)) byStatus.put(((GoodsReceipt.Status) r[0]).name(), (Long) r[1]);
        return PageDto.of(p, r -> new ReceiptRow(r.getId(), r.getCode(), r.getStatus(), r.getSupplierId(), r.getSupplierName(),
                r.getTotal(), r.getPaidAmount(), r.getLines().size(), r.getCreatedBy(), r.getCreatedAt(), r.getPostedAt()),
                Map.of("count", s[0], "total", s[1], "paid", s[2], "countByStatus", byStatus));
    }

    @GetMapping("/{id}")
    public GoodsReceipt get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    public GoodsReceipt create(@RequestBody ReceiptService.ReceiptRequest r, Authentication a) { return service.get(service.create(r, who(a)).getId()); }

    @PutMapping("/{id}")
    public GoodsReceipt update(@PathVariable Long id, @RequestBody ReceiptService.ReceiptRequest r) { service.update(id, r); return service.get(id); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { service.deleteDraft(id); }

    @PostMapping("/{id}/post")
    public GoodsReceipt post(@PathVariable Long id, Authentication a) { service.post(id, who(a)); return service.get(id); }

    @PostMapping("/{id}/cancel")
    public GoodsReceipt cancel(@PathVariable Long id, Authentication a) { service.cancel(id, who(a)); return service.get(id); }
}

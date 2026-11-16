package com.shop.supplier;

import com.shop.common.ApiException;
import com.shop.common.PageDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/** Nha cung cap + cong no NCC. Chi ADMIN (SecurityConfig: anyRequest ADMIN). */
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    private final SupplierRepository suppliers;
    private final SupplierLedgerRepository ledger;
    private final SupplierService service;

    public SupplierController(SupplierRepository suppliers, SupplierLedgerRepository ledger, SupplierService service) {
        this.suppliers = suppliers; this.ledger = ledger; this.service = service;
    }

    public record SupplierRequest(@NotBlank String name, String phone, String address, BigDecimal initialDebt) {}
    public record PaymentRequest(@NotNull @DecimalMin("0.01") BigDecimal amount, String note) {}
    public record SetDebtRequest(@NotNull @DecimalMin("0") BigDecimal debt, String note) {}

    private static final Map<String, String> SORTABLE = Map.of("name", "name", "debt", "debt");

    @GetMapping
    @Transactional(readOnly = true)
    public PageDto<Supplier> list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "false") boolean debt,
                                  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(defaultValue = "") String sort) {
        String like = PageDto.like(q);
        var p = suppliers.page(like, debt, PageDto.pageable(page, size, sort, SORTABLE, Sort.by("name").and(Sort.by("id"))));
        Object[] s = suppliers.summary(like, debt).get(0);
        return PageDto.of(p, x -> x, Map.of("count", s[0], "totalDebt", s[1],
                "suppliersInDebt", s[2] == null ? 0 : ((Number) s[2]).longValue()));
    }

    @GetMapping("/{id}")
    public Supplier get(@PathVariable Long id) { return service.find(id); }

    @PostMapping
    @Transactional
    public Supplier create(@Valid @RequestBody SupplierRequest r) {
        String name = r.name().trim();
        if (suppliers.existsByActiveTrueAndNameIgnoreCase(name)) throw ApiException.conflict("Tên nhà cung cấp đã tồn tại");
        Supplier s = new Supplier();
        s.setName(name);
        s.setPhone(r.phone());
        s.setAddress(r.address());
        s = suppliers.save(s);
        if (r.initialDebt() != null && r.initialDebt().signum() > 0)
            service.apply(s, SupplierLedger.Type.MANUAL, r.initialDebt(), null, "Nợ ban đầu");
        return s;
    }

    @PutMapping("/{id}")
    @Transactional
    public Supplier update(@PathVariable Long id, @Valid @RequestBody SupplierRequest r) {
        Supplier s = service.find(id);
        s.setName(r.name().trim());
        s.setPhone(r.phone());
        s.setAddress(r.address());
        return s;
    }

    /** Xoa mem: phieu nhap cu van giu ten NCC. */
    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) { service.find(id).setActive(false); }

    @GetMapping("/{id}/ledger")
    public PageDto<SupplierLedger> ledger(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return PageDto.of(ledger.findBySupplierIdOrderByIdDesc(id, PageDto.pageable(page, size, null, Map.of(), Sort.unsorted())), x -> x, null);
    }

    @PostMapping("/{id}/payments")
    public Supplier pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest r) { return service.pay(id, r.amount(), r.note()); }

    @PostMapping("/{id}/debt-adjustments")
    public Supplier setDebt(@PathVariable Long id, @Valid @RequestBody SetDebtRequest r) { return service.setDebt(id, r.debt(), r.note()); }
}

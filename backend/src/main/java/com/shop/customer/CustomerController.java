package com.shop.customer;

import com.shop.common.ApiException;
import com.shop.common.PageDto;
import com.shop.order.Order;
import com.shop.order.OrderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerRepository customers;
    private final DebtLedgerRepository ledger;
    private final DebtService debts;
    private final OrderRepository orders;

    public CustomerController(CustomerRepository customers, DebtLedgerRepository ledger, DebtService debts, OrderRepository orders) {
        this.customers = customers; this.ledger = ledger; this.debts = debts; this.orders = orders;
    }

    public record CustomerRequest(@NotBlank String name, String address, String phone, BigDecimal initialDebt) {}
    public record PaymentRequest(@NotNull @DecimalMin("0.01") BigDecimal amount, String note) {}
    public record SetDebtRequest(@NotNull @DecimalMin("0") BigDecimal debt, String note) {}
    public record ReceiptRow(LocalDateTime date, BigDecimal oldDebt, BigDecimal amount, BigDecimal remainingDebt) {}
    public record Receipt(Customer customer, BigDecimal currentDebt, List<ReceiptRow> rows) {}

    private static final Map<String, String> SORTABLE = Map.of("name", "name", "debt", "debt", "phone", "phone");

    /** Danh sach khach co phan trang. summary: count, totalDebt, customersInDebt (tren toan bo ket qua loc). */
    @GetMapping
    @Transactional(readOnly = true)
    public PageDto<Customer> list(@RequestParam(defaultValue = "") String q,
                                  @RequestParam(defaultValue = "false") boolean debt,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(defaultValue = "") String sort) {
        String like = PageDto.like(q);
        var p = customers.page(like, debt, PageDto.pageable(page, size, sort, SORTABLE, Sort.by("name").and(Sort.by("id"))));
        Object[] s = customers.summary(like, debt).get(0);
        return PageDto.of(p, c -> c, Map.of("count", s[0], "totalDebt", s[1],
                "customersInDebt", s[2] == null ? 0 : ((Number) s[2]).longValue()));
    }

    @GetMapping("/{id}")
    public Customer get(@PathVariable Long id) {
        return customers.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
    }

    /** App cu: khong cho trung ten khach; duoc nhap no ban dau khi tao. */
    @PostMapping
    @Transactional
    public Customer create(@Valid @RequestBody CustomerRequest r, org.springframework.security.core.Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && r.initialDebt() != null && r.initialDebt().signum() != 0)
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Chỉ quản trị được nhập nợ ban đầu");
        String name = r.name().trim();
        if (customers.existsByActiveTrueAndNameIgnoreCase(name)) throw ApiException.conflict("Tên khách hàng đã tồn tại");
        Customer c = new Customer();
        c.setName(name);
        c.setAddress(r.address());
        c.setPhone(r.phone());
        c = customers.save(c);
        if (r.initialDebt() != null && r.initialDebt().signum() > 0) {
            debts.apply(c, DebtLedger.Type.MANUAL, r.initialDebt(), null, "Nợ ban đầu");
        }
        return c;
    }

    @PutMapping("/{id}")
    @Transactional
    public Customer update(@PathVariable Long id, @Valid @RequestBody CustomerRequest r) {
        Customer c = customers.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        c.setName(r.name().trim());
        c.setAddress(r.address());
        c.setPhone(r.phone());
        return c;
    }

    /** Xoa mem. Khong xoa hoa don/lich su cua khach. */
    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        customers.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng")).setActive(false);
    }

    /** Dong so no kem ma hoa don (de bam mo hoa don). */
    public record LedgerRow(Long id, DebtLedger.Type type, BigDecimal amount, BigDecimal balanceAfter,
                            Long orderId, String orderCode, String note, LocalDateTime createdAt) {}

    /** So cong no, moi nhat truoc, co phan trang. */
    @GetMapping("/{id}/debt-ledger")
    @Transactional(readOnly = true)
    public PageDto<LedgerRow> history(@PathVariable Long id,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        var p = ledger.findByCustomerIdOrderByIdDesc(id, PageDto.pageable(page, size, null, Map.of(), Sort.unsorted()));
        List<Long> orderIds = p.getContent().stream().map(DebtLedger::getOrderId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> codes = new java.util.HashMap<>();
        orders.findAllById(orderIds).forEach(o -> codes.put(o.getId(), o.getCode()));
        return PageDto.of(p, l -> new LedgerRow(l.getId(), l.getType(), l.getAmount(), l.getBalanceAfter(),
                l.getOrderId(), codes.get(l.getOrderId()), l.getNote(), l.getCreatedAt()), null);
    }

    /** Thay man ThanhToanNo cu: so tien tra <= so no. */
    @PostMapping("/{id}/debt-payments")
    public Customer pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest r) {
        return debts.pay(id, r.amount(), r.note());
    }

    /** Sua so no truc tiep (o "No" man Customers, nut "Cap nhat" PayDialog cu). Chi ADMIN (SecurityConfig). */
    @PostMapping("/{id}/debt-adjustments")
    public Customer setDebt(@PathVariable Long id, @Valid @RequestBody SetDebtRequest r) {
        return debts.setDebt(id, r.debt(), r.note());
    }

    /**
     * Du lieu "Bien lai thanh toan cong no" (ThanhToanNoReport.jrxml cu): cac lan tra no trong chu ky no hien tai.
     * App cu danh dau Deleted=1 cho lich su khi no ve 0 hoac khi sua no tay -> chu ky bat dau sau lan gan nhat do.
     * Cot "No cu" cua lan tra luc chot hoa don = no cu truoc hoa don (giong PaymentHistory.OldDebt cu).
     */
    @GetMapping("/{id}/payment-receipt")
    @Transactional(readOnly = true)
    public Receipt receipt(@PathVariable Long id) {
        Customer c = customers.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        List<DebtLedger> all = ledger.findByCustomerIdOrderByIdDesc(id); // moi nhat truoc
        List<ReceiptRow> rows = new ArrayList<>();
        for (DebtLedger l : all) {
            // het chu ky: lan gan nhat no ve 0, hoac lan sua no tay
            if (l.getType() == DebtLedger.Type.MANUAL || l.getBalanceAfter().signum() == 0) break;
            if (l.getType() == DebtLedger.Type.PAYMENT) {
                BigDecimal amount = l.getAmount().negate();
                BigDecimal oldDebt = l.getBalanceAfter().add(amount);
                if (l.getOrderId() != null) {
                    Order o = orders.findById(l.getOrderId()).orElse(null);
                    if (o != null) { oldDebt = o.getPreviousDebt(); amount = o.getPaidAmount(); }
                }
                rows.add(new ReceiptRow(l.getCreatedAt(), oldDebt, amount, l.getBalanceAfter()));
            }
        }
        java.util.Collections.reverse(rows); // app cu: ORDER BY PaymentDate ASC
        return new Receipt(c, c.getDebt(), rows);
    }
}

package com.shop.customer;

import com.shop.common.ApiException;
import com.shop.common.TextSearch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerRepository customers;
    private final DebtLedgerRepository ledger;
    private final DebtService debts;
    private final DebtStatementService statements;

    public CustomerController(CustomerRepository customers, DebtLedgerRepository ledger, DebtService debts,
                              DebtStatementService statements) {
        this.customers = customers; this.ledger = ledger; this.debts = debts; this.statements = statements;
    }

    public record CustomerRequest(@NotBlank String name, String address, String phone, BigDecimal initialDebt) {}
    public record PaymentRequest(@NotNull @DecimalMin("0.01") BigDecimal amount, String note, DebtLedger.Method method) {}
    /** note = ly do dieu chinh (bat buoc). */
    public record SetDebtRequest(@NotNull @DecimalMin("0") BigDecimal debt, String note) {}
    public record VoidRequest(String reason) {}
    /** Ket qua thu no: khach sau khi thu + so phieu de in ngay. */
    public record PaymentResult(Customer customer, Long ledgerId, String receiptNo) {}
    /** Mot dong so no tren man hinh: da dien giai san (giong dong tren phieu thu / sao ke). */
    public record LedgerRow(Long id, DebtLedger.Type type, String description, BigDecimal increase, BigDecimal decrease,
                            BigDecimal balanceAfter, LocalDateTime createdAt, String createdBy, String receiptNo,
                            String method, boolean voided, boolean canVoid) {}

    @GetMapping
    public List<Customer> list(@RequestParam(defaultValue = "") String q) {
        // khong dau, theo tu: "tuan han" thay "Anh Tuấn thợ hàn"; so dien thoai tim theo doan so
        return TextSearch.rank(customers.findByActiveTrueOrderByName(), q, c -> new String[]{c.getName(), c.getPhone()}, c -> 0L);
    }

    /** App cu: khong cho trung ten khach; duoc nhap no ban dau khi tao. */
    @PostMapping
    @Transactional
    public Customer create(@Valid @RequestBody CustomerRequest r, org.springframework.security.core.Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && r.initialDebt() != null && r.initialDebt().signum() != 0)
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Chi quan tri duoc nhap no ban dau");
        String name = r.name().trim();
        if (customers.existsByActiveTrueAndNameIgnoreCase(name)) throw ApiException.conflict("Ten khach hang da ton tai");
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
        Customer c = customers.findById(id).orElseThrow(() -> ApiException.notFound("Khong tim thay khach hang"));
        if (customers.existsByActiveTrueAndNameIgnoreCaseAndIdNot(r.name().trim(), id)) throw ApiException.conflict("Ten khach hang da ton tai");
        c.setName(r.name().trim());
        c.setAddress(r.address());
        c.setPhone(r.phone());
        return c;
    }

    /**
     * Xoa mem. Khong xoa hoa don/lich su cua khach. Khach con no thi khong an duoc: no se bien khoi "Khach dang no"
     * va khong ai thu duoc nua -> thu het, hoac dieu chinh ve 0 (co ly do, vd "No kho doi") truoc.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        Customer c = customers.findById(id).orElseThrow(() -> ApiException.notFound("Khong tim thay khach hang"));
        if (c.getDebt().signum() > 0)
            throw ApiException.conflict("Khach con no " + c.getDebt().stripTrailingZeros().toPlainString()
                    + ". Thu het hoac dieu chinh no ve 0 (ghi ly do) truoc khi an");
        c.setActive(false);
    }

    @GetMapping("/{id}")
    public Customer get(@PathVariable Long id) {
        return customers.findById(id).orElseThrow(() -> ApiException.notFound("Khong tim thay khach hang"));
    }

    /** So no, moi nhat truoc, da dien giai (Mua HD..., Thu no PT..., Dieu chinh: ly do...). */
    @GetMapping("/{id}/debt-ledger")
    @Transactional(readOnly = true)
    public List<LedgerRow> history(@PathVariable Long id) {
        List<DebtLedger> rows = ledger.findByCustomerIdOrderByIdDesc(id);
        DebtStatementService.Lookup k = statements.lookup(rows);
        return rows.stream().map(l -> {
            BigDecimal a = l.getAmount();
            boolean isReceipt = l.getType() == DebtLedger.Type.PAYMENT && l.getReceiptNo() != null;
            boolean voided = k.voided().contains(l.getId());
            return new LedgerRow(l.getId(), l.getType(), DebtStatementService.describe(l, k),
                    a.signum() > 0 ? a : null, a.signum() < 0 ? a.negate() : null, l.getBalanceAfter(), l.getCreatedAt(),
                    l.getCreatedBy(), l.getReceiptNo(), DebtStatementService.methodLabel(l.getMethod()), voided, isReceipt && !voided);
        }).toList();
    }

    /** Thu no (man ThanhToanNo cu): so tien tra <= so no; tra ve so phieu de in. */
    @PostMapping("/{id}/debt-payments")
    public PaymentResult pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest r) {
        DebtLedger l = debts.pay(id, r.amount(), r.note(), r.method());
        return new PaymentResult(get(id), l.getId(), l.getReceiptNo());
    }

    /** Huy phieu thu nhap nham (chi ADMIN): them dong dao nguoc, khong xoa. */
    @PostMapping("/debt-payments/{ledgerId}/void")
    public Customer voidPayment(@PathVariable Long ledgerId, @RequestBody VoidRequest r) {
        DebtLedger v = debts.voidPayment(ledgerId, r.reason());
        return get(v.getCustomerId());
    }

    /** Dieu chinh so no (o "No" man Customers cu, nut "Cap nhat" PayDialog cu). Bat buoc ly do. Chi ADMIN (SecurityConfig). */
    @PostMapping("/{id}/debt-adjustments")
    public Customer setDebt(@PathVariable Long id, @Valid @RequestBody SetDebtRequest r) {
        return debts.setDebt(id, r.debt(), r.note());
    }

    /** Du lieu phieu thu no (in / in lai, so lieu co dinh). */
    @GetMapping("/debt-payments/{ledgerId}/receipt")
    public DebtStatementService.Doc receipt(@PathVariable Long ledgerId) {
        return statements.receipt(ledgerId);
    }

    /** Du lieu sao ke cong no theo khoang ngay. */
    @GetMapping("/{id}/statement")
    public DebtStatementService.Doc statement(@PathVariable Long id,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return statements.statement(id, from, to);
    }
}

package com.shop.customer;

import com.shop.common.ApiException;
import com.shop.common.TextSearch;
import com.shop.order.Order;
import com.shop.order.OrderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @GetMapping
    public List<Customer> list(@RequestParam(defaultValue = "") String q) {
        // khong dau, theo tu: "tuan han" thay "Anh Tuấn thợ hàn"; so dien thoai tim theo doan so
        return TextSearch.filter(customers.findByActiveTrueOrderByName(), q, c -> new String[]{c.getName(), c.getPhone()});
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
            debts.apply(c, DebtLedger.Type.MANUAL, r.initialDebt(), null, "No ban dau");
        }
        return c;
    }

    @PutMapping("/{id}")
    @Transactional
    public Customer update(@PathVariable Long id, @Valid @RequestBody CustomerRequest r) {
        Customer c = customers.findById(id).orElseThrow(() -> ApiException.notFound("Khong tim thay khach hang"));
        c.setName(r.name().trim());
        c.setAddress(r.address());
        c.setPhone(r.phone());
        return c;
    }

    /** Xoa mem. Khong xoa hoa don/lich su cua khach. */
    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        customers.findById(id).orElseThrow(() -> ApiException.notFound("Khong tim thay khach hang")).setActive(false);
    }

    @GetMapping("/{id}/debt-ledger")
    public List<DebtLedger> history(@PathVariable Long id) {
        return ledger.findByCustomerIdOrderByIdDesc(id);
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
        Customer c = customers.findById(id).orElseThrow(() -> ApiException.notFound("Khong tim thay khach hang"));
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

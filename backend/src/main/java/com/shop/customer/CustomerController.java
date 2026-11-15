package com.shop.customer;

import com.shop.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerRepository customers;
    private final DebtLedgerRepository ledger;
    private final DebtService debts;

    public CustomerController(CustomerRepository customers, DebtLedgerRepository ledger, DebtService debts) {
        this.customers = customers; this.ledger = ledger; this.debts = debts;
    }

    public record CustomerRequest(@NotBlank String name, String address, String phone) {}
    public record PaymentRequest(@NotNull @DecimalMin("0.01") BigDecimal amount, String note) {}

    @GetMapping
    public List<Customer> list(@RequestParam(defaultValue = "") String q) {
        return customers.search(q);
    }

    @PostMapping
    public Customer create(@Valid @RequestBody CustomerRequest r) {
        Customer c = new Customer();
        c.setName(r.name().trim());
        c.setAddress(r.address());
        c.setPhone(r.phone());
        return customers.save(c);
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

    @PostMapping("/{id}/debt-payments")
    public Customer pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest r) {
        return debts.pay(id, r.amount(), r.note());
    }
}

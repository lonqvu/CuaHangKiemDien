package com.shop.customer;

import com.shop.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Moi lan doi cong no deu di qua day de luon co 1 dong so cai di kem. */
@Service
public class DebtService {
    private final CustomerRepository customers;
    private final DebtLedgerRepository ledger;

    public DebtService(CustomerRepository customers, DebtLedgerRepository ledger) {
        this.customers = customers; this.ledger = ledger;
    }

    /** Goi trong transaction cua noi goi (checkout/cancel) de rollback chung. */
    public void apply(Customer c, DebtLedger.Type type, BigDecimal signedAmount, Long orderId, String note) {
        c.setDebt(c.getDebt().add(signedAmount));
        DebtLedger l = new DebtLedger();
        l.setCustomerId(c.getId());
        l.setType(type);
        l.setAmount(signedAmount);
        l.setBalanceAfter(c.getDebt());
        l.setOrderId(orderId);
        l.setNote(note);
        ledger.save(l);
    }

    /** Giong nut "Cap nhat no cu" (PayDialog) va o "No" (Customers) cua app cu: dat lai so no. */
    @Transactional
    public Customer setDebt(Long customerId, BigDecimal newDebt, String note) {
        if (newDebt == null || newDebt.signum() < 0) throw ApiException.badRequest("Số nợ không hợp lệ");
        Customer c = customers.findById(customerId).orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        BigDecimal diff = newDebt.subtract(c.getDebt());
        apply(c, DebtLedger.Type.MANUAL, diff, null, note == null || note.isBlank() ? "Điều chỉnh nợ" : note);
        return c;
    }

    @Transactional
    public Customer pay(Long customerId, BigDecimal amount, String note) {
        if (amount == null || amount.signum() <= 0) throw ApiException.badRequest("Số tiền trả phải lớn hơn 0");
        Customer c = customers.findById(customerId).orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        if (amount.compareTo(c.getDebt()) > 0) throw ApiException.badRequest("Số tiền trả lớn hơn số nợ hiện tại");
        apply(c, DebtLedger.Type.PAYMENT, amount.negate(), null, note);
        return c;
    }
}

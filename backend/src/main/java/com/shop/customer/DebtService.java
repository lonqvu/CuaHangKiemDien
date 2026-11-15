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

    @Transactional
    public Customer pay(Long customerId, BigDecimal amount, String note) {
        if (amount == null || amount.signum() <= 0) throw ApiException.badRequest("So tien tra phai lon hon 0");
        Customer c = customers.findById(customerId).orElseThrow(() -> ApiException.notFound("Khong tim thay khach hang"));
        if (amount.compareTo(c.getDebt()) > 0) throw ApiException.badRequest("So tien tra lon hon so no hien tai");
        apply(c, DebtLedger.Type.PAYMENT, amount.negate(), null, note);
        return c;
    }
}

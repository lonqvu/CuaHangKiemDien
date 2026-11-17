package com.shop.customer;

import com.shop.common.ApiException;
import com.shop.common.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Moi lan doi cong no deu di qua day de luon co 1 dong so cai di kem.
 * So cai la nguon duy nhat de in phieu thu / sao ke: khong co thao tac nao sua thang so no ma khong ghi so.
 */
@Service
public class DebtService {
    private final CustomerRepository customers;
    private final DebtLedgerRepository ledger;

    public DebtService(CustomerRepository customers, DebtLedgerRepository ledger) {
        this.customers = customers; this.ledger = ledger;
    }

    /** Goi trong transaction cua noi goi (checkout/cancel) de rollback chung. */
    public DebtLedger apply(Customer c, DebtLedger.Type type, BigDecimal signedAmount, Long orderId, String note) {
        c.setDebt(c.getDebt().add(signedAmount));
        DebtLedger l = new DebtLedger();
        l.setCustomerId(c.getId());
        l.setType(type);
        l.setAmount(signedAmount);
        l.setBalanceAfter(c.getDebt());
        l.setOrderId(orderId);
        l.setNote(note);
        l.setCreatedBy(CurrentUser.name());
        return ledger.save(l);
    }

    /**
     * Dieu chinh no (o "No" man Customers cu, nut "Cap nhat" no cu o PayDialog cu). Bat buoc ly do de sau nay biet
     * vi sao no doi; tren phieu thu / sao ke hien thanh dong "Dieu chinh: ly do" (khong xoa hay cat lich su).
     */
    @Transactional
    public Customer setDebt(Long customerId, BigDecimal newDebt, String reason) {
        if (newDebt == null || newDebt.signum() < 0) throw ApiException.badRequest("Số nợ không hợp lệ");
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("Nhập lý do điều chỉnh nợ");
        Customer c = find(customerId);
        BigDecimal diff = newDebt.subtract(c.getDebt());
        if (diff.signum() == 0) return c; // khong doi -> khong ghi so
        apply(c, DebtLedger.Type.MANUAL, diff, null, reason.trim());
        return c;
    }

    /** Thu no (man ThanhToanNo cu): so tien <= so no; moi lan thu co 1 so phieu de in / in lai. */
    @Transactional
    public DebtLedger pay(Long customerId, BigDecimal amount, String note, DebtLedger.Method method) {
        if (amount == null || amount.signum() <= 0) throw ApiException.badRequest("Số tiền trả phải lớn hơn 0");
        Customer c = find(customerId);
        if (amount.compareTo(c.getDebt()) > 0) throw ApiException.badRequest("Số tiền trả lớn hơn số nợ hiện tại");
        DebtLedger l = apply(c, DebtLedger.Type.PAYMENT, amount.negate(), null, note == null || note.isBlank() ? null : note.trim());
        l.setReceiptNo(String.format("PT%06d", ledger.nextReceiptNo()));
        l.setMethod(method == null ? DebtLedger.Method.CASH : method);
        return l;
    }

    /** Thu tien mat (code cu / du lieu mau / test). */
    @Transactional
    public Customer pay(Long customerId, BigDecimal amount, String note) {
        pay(customerId, amount, note, DebtLedger.Method.CASH);
        return find(customerId);
    }

    /**
     * Huy phieu thu (thu nham so tien...): khong xoa, them 1 dong VOID dao nguoc -> no tang lai dung so da thu.
     * Phieu bi huy in lai se co chu "DA HUY". Chi huy duoc phieu thu no (khong phai tien tra luc mua - huy hoa don thay the).
     */
    @Transactional
    public DebtLedger voidPayment(Long ledgerId, String reason) {
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("Nhập lý do hủy phiếu thu");
        DebtLedger p = ledger.findById(ledgerId).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu thu"));
        if (p.getType() != DebtLedger.Type.PAYMENT || p.getReceiptNo() == null)
            throw ApiException.badRequest("Chỉ hủy được phiếu thu nợ (tiền trả lúc mua hàng: hủy hóa đơn)");
        if (ledger.existsByReversalOf(p.getId())) throw ApiException.conflict("Phiếu thu " + p.getReceiptNo() + " đã hủy trước đó");
        Customer c = find(p.getCustomerId());
        DebtLedger v = apply(c, DebtLedger.Type.VOID, p.getAmount().negate(), null, reason.trim());
        v.setReversalOf(p.getId());
        return v;
    }

    private Customer find(Long id) {
        return customers.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
    }
}

package com.shop.supplier;

import com.shop.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class SupplierService {
    private final SupplierRepository suppliers;
    private final SupplierLedgerRepository ledger;

    public SupplierService(SupplierRepository suppliers, SupplierLedgerRepository ledger) {
        this.suppliers = suppliers; this.ledger = ledger;
    }

    public Supplier find(Long id) {
        return suppliers.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp"));
    }

    /** Goi trong transaction cua noi goi (ghi so / huy phieu nhap) de rollback chung. */
    public void apply(Supplier s, SupplierLedger.Type type, BigDecimal signedAmount, Long receiptId, String note) {
        if (signedAmount.signum() == 0) return;
        s.setDebt(s.getDebt().add(signedAmount));
        SupplierLedger l = new SupplierLedger();
        l.setSupplierId(s.getId());
        l.setType(type);
        l.setAmount(signedAmount);
        l.setBalanceAfter(s.getDebt());
        l.setReceiptId(receiptId);
        l.setNote(note);
        ledger.save(l);
    }

    @Transactional
    public Supplier pay(Long id, BigDecimal amount, String note) {
        if (amount == null || amount.signum() <= 0) throw ApiException.badRequest("Số tiền trả phải lớn hơn 0");
        Supplier s = find(id);
        if (amount.compareTo(s.getDebt()) > 0) throw ApiException.badRequest("Số tiền trả lớn hơn số đang nợ nhà cung cấp");
        apply(s, SupplierLedger.Type.PAYMENT, amount.negate(), null, note == null || note.isBlank() ? "Trả nhà cung cấp" : note);
        return s;
    }

    @Transactional
    public Supplier setDebt(Long id, BigDecimal newDebt, String note) {
        if (newDebt == null || newDebt.signum() < 0) throw ApiException.badRequest("Số nợ không hợp lệ");
        Supplier s = find(id);
        apply(s, SupplierLedger.Type.MANUAL, newDebt.subtract(s.getDebt()), null, note == null || note.isBlank() ? "Điều chỉnh nợ" : note);
        return s;
    }
}

package com.shop.report.jasper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Du lieu cho ThanhToanNoReport.jrxml (bien lai thanh toan cong no cua app cu).
 * Cau SQL cu: Customer LEFT JOIN PaymentHistory WHERE Deleted = 0 ORDER BY PaymentDate ASC.
 * Ten cot va kieu (Double, Timestamp) dung nhu <field> trong file .jrxml.
 */
public final class DebtReceiptData {

    public record Payment(LocalDateTime date, BigDecimal oldDebt, BigDecimal amount, BigDecimal remainingDebt) {}

    private DebtReceiptData() {}

    public static List<Map<String, ?>> rows(long customerId, String name, String address, String phone,
                                            BigDecimal debt, List<Payment> payments) {
        List<Map<String, ?>> out = new ArrayList<>();
        for (Payment p : payments) {
            Map<String, Object> r = new HashMap<>();
            r.put("ID", (int) customerId);
            r.put("CustomerName", name);
            r.put("Address", address == null ? "" : address); // tranh in chu "null"
            r.put("PhoneNumber", phone == null ? "" : phone);
            r.put("Debt", d(debt));
            r.put("OldDebt", null);
            r.put("Amount", d(p.amount()));
            r.put("PaymentDate", p.date() == null ? null : Timestamp.valueOf(p.date()));
            r.put("RemainingDebt", d(p.remainingDebt()));
            r.put("PaymentOldDebt", d(p.oldDebt()));
            out.add(r);
        }
        return out;
    }

    private static Double d(BigDecimal b) { return b == null ? null : b.doubleValue(); }
}

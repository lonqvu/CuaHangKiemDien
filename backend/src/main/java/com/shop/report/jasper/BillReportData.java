package com.shop.report.jasper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Dung du lieu cho Bill.jrxml (hoa don cua app cu), lam y het cau SQL nam trong file .jrxml:
 *  - RawBill: cac dong hang cua hoa don
 *  - TonGop: dong co SoTam != 0, gop theo ProductID + SoTam + HeSo + Price + UnitName + Products_Name;
 *            Bill_Amount = MIN, IntoMoney/TongSoLuong/SoTam = SUM, Line_Count = COUNT,
 *            Amount_List = cac Amount (tang dan) noi bang " + "
 *  - HangThuong: dong co SoTam = 0, giu nguyen
 *  - ORDER BY Products_Name DESC, HeSo, SoTam, Bill_ID; STT = ROW_NUMBER()
 * Ten cot (key cua Map) va kieu du lieu dung nhu <field> khai bao trong Bill.jrxml.
 * Lop nay chi dung JDK de co the chay thu doc lap voi Spring.
 */
public final class BillReportData {

    public record Line(long id, Long productId, String productName, String unit, BigDecimal amount, int soTam,
                       BigDecimal heSo, BigDecimal price, BigDecimal tongSoLuong, BigDecimal intoMoney) {}

    public record Header(String orderCode, LocalDateTime date, String customer, String address, String phone,
                         BigDecimal oldDebt, BigDecimal totalMoneyBill, BigDecimal totalMoneyOrder, BigDecimal payMoney,
                         BigDecimal debtBack, BigDecimal shippingFee, String staff, String note) {}

    private BillReportData() {}

    public static List<Map<String, ?>> rows(Header h, List<Line> lines) {
        List<Map<String, Object>> bill = new ArrayList<>();

        // Buoc 2: TonGop
        Map<List<Object>, List<Line>> groups = new LinkedHashMap<>();
        for (Line l : lines) {
            if (l.soTam() == 0) continue;
            List<Object> key = Arrays.asList(l.productId(), l.soTam(), norm(l.heSo()), norm(l.price()),
                    l.unit() == null ? "" : l.unit(), l.productName());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
        }
        for (List<Line> g : groups.values()) {
            Line f = g.get(0);
            Map<String, Object> r = new HashMap<>();
            r.put("Bill_ID", toInt(g.stream().mapToLong(Line::id).min().getAsLong()));
            r.put("Bill_Amount", g.stream().map(Line::amount).min(Comparator.naturalOrder()).orElse(null));
            r.put("Bill_IntoMoney", sum(g.stream().map(Line::intoMoney).toList()));
            r.put("Bill_ProductID", f.productId() == null ? null : toInt(f.productId()));
            r.put("Bill_SoTam", f.soTam());
            r.put("Bill_Price", f.price());
            r.put("Bill_UnitName", f.unit());
            r.put("Bill_HeSo", f.heSo());
            r.put("Bill_TongSoLuong", sum(g.stream().map(Line::tongSoLuong).toList()));
            r.put("Bill_SoTam_Total", g.stream().mapToInt(Line::soTam).sum());
            r.put("Line_Count", g.size());
            r.put("Amount_List", String.join(" + ", g.stream().map(Line::amount).sorted().map(BillReportData::sqlFloatText).toList()));
            r.put("Products_Name", f.productName());
            r.put("_sortId", g.stream().mapToLong(Line::id).min().getAsLong());
            bill.add(r);
        }
        // Buoc 3: HangThuong
        for (Line l : lines) {
            if (l.soTam() != 0) continue;
            Map<String, Object> r = new HashMap<>();
            r.put("Bill_ID", toInt(l.id()));
            r.put("Bill_Amount", l.amount());
            r.put("Bill_IntoMoney", l.intoMoney());
            r.put("Bill_ProductID", l.productId() == null ? null : toInt(l.productId()));
            r.put("Bill_SoTam", 0);
            r.put("Bill_Price", l.price());
            r.put("Bill_UnitName", l.unit());
            r.put("Bill_HeSo", l.heSo());
            r.put("Bill_TongSoLuong", l.tongSoLuong());
            r.put("Bill_SoTam_Total", 0);
            r.put("Line_Count", 1);
            r.put("Amount_List", sqlFloatText(l.amount()));
            r.put("Products_Name", l.productName());
            r.put("_sortId", l.id());
            bill.add(r);
        }

        // Buoc 5: ORDER BY Products_Name DESC, Bill_HeSo, Bill_SoTam, Bill_ID
        Collator vi = Collator.getInstance(new Locale("vi", "VN"));
        vi.setStrength(Collator.SECONDARY); // khong phan biet hoa/thuong, co phan biet dau (giong collation CI_AS)
        bill.sort(Comparator
                .comparing((Map<String, Object> m) -> (String) m.get("Products_Name"), Comparator.nullsFirst(vi)).reversed()
                .thenComparing(m -> zero((BigDecimal) m.get("Bill_HeSo")))
                .thenComparing(m -> (Integer) m.get("Bill_SoTam"))
                .thenComparing(m -> (Long) m.get("_sortId")));

        List<Map<String, ?>> out = new ArrayList<>();
        long stt = 1;
        for (Map<String, Object> r : bill) {
            r.remove("_sortId");
            r.put("STT", stt++);
            r.put("Bill_OrderID", h.orderCode());
            r.put("Products_UnitName", r.get("Bill_UnitName"));
            r.put("Products_Price", r.get("Bill_Price") == null ? null : ((BigDecimal) r.get("Bill_Price")).toPlainString());
            r.put("Orders_ID", h.orderCode());
            r.put("Orders_Date", h.date() == null ? null : Timestamp.valueOf(h.date()));
            r.put("Customer", h.customer());
            r.put("Address", h.address() == null ? "" : h.address()); // mau noi chuoi: null in ra chu "null"
            r.put("Phone_Number", h.phone() == null ? "" : h.phone());
            r.put("Old_Debt", h.oldDebt());
            r.put("Total_Money_Bill", h.totalMoneyBill());
            r.put("Total_Money_Order", h.totalMoneyOrder());
            r.put("Pay_Money", h.payMoney());
            r.put("Debt_Back", h.debtBack());
            r.put("Shipping_Fee", h.shippingFee());
            r.put("Staff", h.staff());
            r.put("Note", h.note());
            out.add(r);
        }
        return out;
    }

    /** CAST(CAST(x AS FLOAT) AS VARCHAR(20)) cua SQL Server: toi da 6 chu so co nghia, bo so 0 thua. */
    static String sqlFloatText(BigDecimal v) {
        if (v == null) return null;
        double d = v.doubleValue();
        if (d != 0 && (Math.abs(d) >= 1e6 || Math.abs(d) < 1e-4)) {
            String s = String.format(Locale.ROOT, "%.5e", d);           // 1.23457e+06
            String[] p = s.split("e");
            String mant = new BigDecimal(p[0]).stripTrailingZeros().toPlainString();
            int exp = Integer.parseInt(p[1]);
            return mant + "e" + (exp < 0 ? "-" : "+") + String.format("%03d", Math.abs(exp)); // 1.23457e+006
        }
        BigDecimal r = new BigDecimal(d).round(new java.math.MathContext(6)).stripTrailingZeros();
        return r.toPlainString();
    }

    private static BigDecimal sum(List<BigDecimal> xs) {
        return xs.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal norm(BigDecimal b) { return b == null ? BigDecimal.ZERO.setScale(0) : b.stripTrailingZeros(); }

    private static BigDecimal zero(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }

    private static Integer toInt(long v) { return (int) v; }
}

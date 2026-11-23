package com.shop.customer;

import com.shop.common.ApiException;
import com.shop.order.Order;
import com.shop.order.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Phieu thu no va sao ke cong no, dung HOAN TOAN tu so cai (debt_ledger).
 *
 * Bat bien cua moi ban in: voi moi dong, con no dong truoc + tang no - giam no = con no dong nay
 * (khach tu cong tay cung ra dung). Thay cho "chu ky bien lai" cua app cu (chi liet ke cac lan tra,
 * khong thay khoan mua them, tra het no thi bien lai trong, in lai thi ra noi dung khac).
 *
 * Phieu thu co dinh: chi dung cac dong so cai co id <= dong thu tien (dong so cai khong bao gio sua),
 * nen in lai luc nao cung ra dung so nhu lan dau. Thay doi duy nhat: phieu bi huy sau do thi co dau "DA HUY".
 */
@Service
public class DebtStatementService {
    /** Toi da so dong phat sinh tren 1 phieu thu; cu hon thi gop thanh dong "So du mang sang". */
    public static final int MAX_LINES = 15;

    private final DebtLedgerRepository ledger;
    private final CustomerRepository customers;
    private final OrderRepository orders;

    public DebtStatementService(DebtLedgerRepository ledger, CustomerRepository customers, OrderRepository orders) {
        this.ledger = ledger; this.customers = customers; this.orders = orders;
    }

    /** Mot dong in. increase/decrease null = de trong; ledgerId null = dong "So du mang sang". */
    public record Line(Long ledgerId, LocalDateTime date, String description, BigDecimal increase, BigDecimal decrease,
                       BigDecimal balance, boolean highlight) {}

    /** Dong tom tat cuoi phieu (nhan, so tien, in dam). */
    public record Total(String label, BigDecimal amount, boolean strong) {}

    public record Doc(String title, String docNo, LocalDateTime date, Long customerId, String customerName, String address,
                      String phone, String collector, String method, boolean voided, String voidReason,
                      List<Line> lines, List<Total> totals) {}

    // ---------------------------------------------------------------- phieu thu

    @Transactional(readOnly = true)
    public Doc receipt(Long ledgerId) {
        DebtLedger p = ledger.findById(ledgerId).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu thu"));
        if (p.getType() != DebtLedger.Type.PAYMENT || p.getReceiptNo() == null)
            throw ApiException.badRequest("Dòng này không phải phiếu thu nợ");
        Customer c = customers.findById(p.getCustomerId()).orElseThrow();
        List<DebtLedger> all = ledger.findByCustomerIdOrderByIdAsc(c.getId());

        int at = indexOf(all, p.getId());
        // dot no hien tai: tu sau lan gan nhat no = 0 TRUOC lan thu nay, den het lan thu nay
        // (lan thu dua no ve 0 van nam trong dot cua no -> tra het no van in duoc phieu day du)
        int start = 0;
        for (int i = at - 1; i >= 0; i--) {
            if (all.get(i).getBalanceAfter().signum() == 0) { start = i + 1; break; }
        }
        List<DebtLedger> window = all.subList(start, at + 1);
        Lookup k = lookup(window); // chi xet cac dong trong cua so -> in lai khong doi

        List<Line> lines = new ArrayList<>();
        int from = Math.max(0, window.size() - MAX_LINES);
        if (from > 0) {
            DebtLedger before = window.get(from - 1);
            lines.add(new Line(null, before.getCreatedAt(), "Số dư mang sang", null, null, before.getBalanceAfter(), false));
        }
        for (DebtLedger l : window.subList(from, window.size())) lines.add(line(l, k, l.getId().equals(p.getId())));

        BigDecimal paid = p.getAmount().negate();
        Optional<DebtLedger> v = all.stream().filter(x -> p.getId().equals(x.getReversalOf())).findFirst();
        List<Total> totals = List.of(
                new Total("Nợ trước khi thu", p.getBalanceAfter().add(paid), false),
                new Total("Số tiền thu" + (p.getMethod() == DebtLedger.Method.TRANSFER ? " (chuyển khoản)" : " (tiền mặt)"), paid, false),
                new Total("Còn nợ", p.getBalanceAfter(), true));
        return new Doc("PHIẾU THU NỢ", "Số: " + p.getReceiptNo(), p.getCreatedAt(), c.getId(), c.getName(), c.getAddress(),
                c.getPhone(), p.getCreatedBy(), methodLabel(p.getMethod()), v.isPresent(), v.map(DebtLedger::getNote).orElse(null),
                lines, totals);
    }

    // ---------------------------------------------------------------- sao ke

    /** Sao ke theo khoang ngay [from, to] (ca hai ngay deu tinh). Null = tu dau / den nay. */
    @Transactional(readOnly = true)
    public Doc statement(Long customerId, LocalDate from, LocalDate to) {
        Customer c = customers.findById(customerId).orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        if (from != null && to != null && from.isAfter(to)) throw ApiException.badRequest("Từ ngày phải trước đến ngày");
        LocalDateTime start = from == null ? null : from.atStartOfDay();
        LocalDateTime end = to == null ? null : to.plusDays(1).atStartOfDay();
        List<DebtLedger> all = ledger.findByCustomerIdOrderByIdAsc(customerId);

        BigDecimal opening = BigDecimal.ZERO;
        List<DebtLedger> in = new ArrayList<>();
        for (DebtLedger l : all) {
            if (start != null && l.getCreatedAt().isBefore(start)) opening = l.getBalanceAfter();
            else if (end == null || l.getCreatedAt().isBefore(end)) in.add(l);
        }
        Lookup k = lookup(all);
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(null, start, "Số dư đầu kỳ", null, null, opening, false));
        BigDecimal inc = BigDecimal.ZERO, dec = BigDecimal.ZERO;
        for (DebtLedger l : in) {
            lines.add(line(l, k, false));
            if (l.getAmount().signum() > 0) inc = inc.add(l.getAmount()); else dec = dec.add(l.getAmount().negate());
        }
        BigDecimal closing = in.isEmpty() ? opening : in.get(in.size() - 1).getBalanceAfter();
        String period = (from == null ? "Từ đầu" : "Từ " + d(from)) + " đến " + (to == null ? "nay" : d(to));
        return new Doc("SAO KÊ CÔNG NỢ", period, LocalDateTime.now(), c.getId(), c.getName(), c.getAddress(), c.getPhone(),
                null, null, false, null, lines, List.of(
                        new Total("Số dư đầu kỳ", opening, false),
                        new Total("Tăng nợ trong kỳ", inc, false),
                        new Total("Giảm nợ trong kỳ", dec, false),
                        new Total("Số dư cuối kỳ", closing, true)));
    }

    // ---------------------------------------------------------------- dien giai tung dong

    /** Du lieu tra cuu de dien giai: hoa don (ma, tien tra lai), so phieu cua dong PAYMENT, phieu da huy. */
    public record Lookup(Map<Long, Order> orders, Map<Long, String> receiptNos, Set<Long> voided) {}

    private Line line(DebtLedger l, Lookup k, boolean highlight) {
        BigDecimal a = l.getAmount();
        return new Line(l.getId(), l.getCreatedAt(), describe(l, k),
                a.signum() > 0 ? a : null, a.signum() < 0 ? a.negate() : null, l.getBalanceAfter(), highlight);
    }

    /** Noi dung dong in, de khach doc hieu vi sao no tang/giam. */
    public static String describe(DebtLedger l, Lookup k) {
        Order o = l.getOrderId() == null ? null : k.orders().get(l.getOrderId());
        String code = o == null ? "" : " " + o.getCode();
        String note = l.getNote() == null || l.getNote().isBlank() ? "" : l.getNote().trim();
        return switch (l.getType()) {
            case ORDER -> "Mua HĐ" + code;
            case PAYMENT -> l.getOrderId() != null ? "Trả khi mua HĐ" + code
                    : "Thu nợ " + l.getReceiptNo() + (l.getMethod() == DebtLedger.Method.TRANSFER ? " (chuyển khoản)" : "")
                    + (k.voided().contains(l.getId()) ? " - ĐÃ HỦY" : "") + (note.isEmpty() ? "" : " - " + note);
            case ADJUST -> "Hủy HĐ" + code + (o != null && o.getRefundAmount() != null && o.getRefundAmount().signum() > 0
                    ? " (đã trả lại khách " + money(o.getRefundAmount()) + ")" : "");
            case REVISE -> "Sửa HĐ" + code + (note.contains("trả lại khách") ? " (" + note.substring(note.indexOf("trả lại khách")) + ")" : "");
            case MANUAL -> manual(l, note);
            case VOID -> "Hủy phiếu thu " + k.receiptNos().getOrDefault(l.getReversalOf(), "") + (note.isEmpty() ? "" : ": " + note);
        };
    }

    /**
     * Dong dieu chinh tay: ghi ro TANG hay GIAM (khach doc hieu ngay), thay cho chu chung "Dieu chinh".
     * So du chuyen tu phan mem cu / no ban dau khi tao khach: ghi dung ten, khong phai tang / giam.
     */
    private static String manual(DebtLedger l, String note) {
        if (note.equals(com.shop.legacy.LegacyImporter.OPENING_NOTE))
            return "Nợ chuyển từ phần mềm cũ (đến " + l.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ")";
        if (note.equalsIgnoreCase("Nợ ban đầu")) return "Nợ ban đầu";
        String kind = l.getAmount().signum() >= 0 ? "Tăng nợ" : "Giảm nợ";
        return note.isEmpty() ? kind : kind + ": " + note;
    }

    /**
     * Chi coi la "da huy" neu dong VOID nam trong rows: phieu thu truyen cua so cua no (in lai khong doi),
     * sao ke / so no tren man hinh truyen ca so cai.
     */
    public Lookup lookup(List<DebtLedger> rows) {
        Map<Long, String> rn = new HashMap<>();
        Set<Long> orderIds = new HashSet<>(), voided = new HashSet<>();
        for (DebtLedger l : rows) {
            if (l.getOrderId() != null) orderIds.add(l.getOrderId());
            if (l.getReceiptNo() != null) rn.put(l.getId(), l.getReceiptNo());
            if (l.getReversalOf() != null) voided.add(l.getReversalOf());
        }
        for (Long id : voided) // phieu bi huy nam ngoai danh sach -> van can so phieu de dien giai dong VOID
            if (!rn.containsKey(id)) ledger.findById(id).ifPresent(x -> rn.put(id, x.getReceiptNo()));
        Map<Long, Order> m = new HashMap<>();
        orders.findAllById(orderIds).forEach(o -> m.put(o.getId(), o));
        return new Lookup(m, rn, voided);
    }

    private static int indexOf(List<DebtLedger> all, Long id) {
        for (int i = 0; i < all.size(); i++) if (all.get(i).getId().equals(id)) return i;
        throw new IllegalStateException("Khong thay dong so cai " + id);
    }

    public static String methodLabel(DebtLedger.Method m) {
        return m == null ? null : m == DebtLedger.Method.TRANSFER ? "Chuyển khoản" : "Tiền mặt";
    }

    private static String d(LocalDate x) {
        return String.format("%02d/%02d/%d", x.getDayOfMonth(), x.getMonthValue(), x.getYear());
    }

    private static String money(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US)); // giong so tren ban in (en-US)
        return f.format(v);
    }
}

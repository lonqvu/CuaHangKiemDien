package com.shop.legacy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Doi chieu TUNG DONG giua du lieu cu (file JSON) va database sau khi nap.
 * Tu tinh lai ket qua mong doi tu du lieu cu, KHONG goi LegacyImporter -> bat duoc loi cua buoc nap.
 * Cung cach voi tools/chuyen-du-lieu/kiem-tra.cjs.
 */
@Component
public class LegacyVerifier {
    private final JdbcTemplate jdbc;

    public LegacyVerifier(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Line(String label, String before, String after, boolean ok) {}

    public record Report(boolean ok, List<Line> summary, List<String> anomalies, List<String> errors, int errorCount) {}

    private static final int MAX_ERRORS = 200;

    public Report verify(LegacyData d) {
        List<String> errors = new ArrayList<>();
        int[] count = {0};
        var err = new Object() {
            void add(String g, String m) { count[0]++; if (errors.size() < MAX_ERRORS) errors.add("[" + g + "] " + m); }
            void eq(String g, String what, Object a, Object b) { if (!Objects.equals(a, b)) add(g, what + ": cũ=" + a + " mới=" + b); }
        };

        // ---------- Loai hang
        Map<Long, String> catName = new HashMap<>();
        jdbc.query("SELECT id, name FROM category", rs -> { catName.put(rs.getLong(1), rs.getString(2)); });
        Map<Integer, String> oldCat = new HashMap<>();
        Set<String> distinct = new HashSet<>();
        for (var c : d.classify()) { oldCat.put(c.id(), low(norm(c.name()))); distinct.add(low(norm(c.name()))); }
        err.eq("loại hàng", "số loại (sau khi gộp trùng tên)", distinct.size(), catName.size());
        Set<String> newCats = new HashSet<>();
        catName.values().forEach(n -> newCats.add(low(n)));
        for (String k : distinct) if (!newCats.contains(k)) err.add("loại hàng", "thiếu loại \"" + k + "\"");

        // ---------- San pham
        Map<Long, Map<String, Object>> prod = rows("SELECT id, category_id, name, unit, price, active FROM product");
        err.eq("sản phẩm", "số sản phẩm", d.products().size(), prod.size());
        for (var o : d.products()) {
            var n = prod.get(o.id().longValue());
            if (n == null) { err.add("sản phẩm", "thiếu ID " + o.id() + " " + o.name()); continue; }
            String w = "ID " + o.id();
            err.eq("sản phẩm", w + " tên", norm(o.name()), n.get("name"));
            err.eq("sản phẩm", w + " đơn vị", norm(o.unit()), n.get("unit"));
            err.eq("sản phẩm", w + " giá", vnd(c2(o.price())), vnd((BigDecimal) n.get("price")));
            Object cid = n.get("category_id");
            err.eq("sản phẩm", w + " loại", oldCat.get(o.classifyId()), cid == null ? null : low(catName.get(((Number) cid).longValue())));
            if (!Boolean.TRUE.equals(n.get("active"))) err.add("sản phẩm", w + " bị ẩn");
        }
        err.eq("sản phẩm", "số dòng tồn kho (bắt đầu 0)", d.products().size(),
                jdbc.queryForObject("SELECT COUNT(*) FROM stock_balance WHERE on_hand = 0", Integer.class));

        // ---------- Khach hang + cong no
        Map<Long, Map<String, Object>> cust = rows("SELECT id, name, address, phone, debt FROM customer");
        List<Map<String, Object>> ledger = jdbc.queryForList("SELECT customer_id, type, amount, balance_after FROM debt_ledger ORDER BY id");
        err.eq("khách hàng", "số khách", d.customers().size(), cust.size());
        BigDecimal oldDebt = BigDecimal.ZERO, newDebt = BigDecimal.ZERO;
        int withDebt = 0;
        for (var o : d.customers()) {
            var n = cust.get(o.id().longValue());
            if (n == null) { err.add("khách hàng", "thiếu ID " + o.id() + " " + o.name()); continue; }
            String w = o.name() + " (ID " + o.id() + ")";
            err.eq("khách hàng", w + " tên", norm(o.name()), n.get("name"));
            err.eq("khách hàng", w + " SĐT", norm(o.phone()), n.get("phone"));
            err.eq("khách hàng", w + " địa chỉ", norm(o.address()), n.get("address"));
            BigDecimal od = Optional.ofNullable(c2(o.debt())).orElse(BigDecimal.ZERO.setScale(2)), nd = (BigDecimal) n.get("debt");
            oldDebt = oldDebt.add(od); newDebt = newDebt.add(nd);
            err.eq("công nợ", w + " nợ", vnd(od), vnd(nd));
            var led = ledger.stream().filter(l -> ((Number) l.get("customer_id")).longValue() == o.id()).toList();
            if (od.signum() != 0) {
                withDebt++;
                boolean good = led.size() == 1 && "MANUAL".equals(led.get(0).get("type"))
                        && ((BigDecimal) led.get(0).get("amount")).compareTo(od) == 0
                        && ((BigDecimal) led.get(0).get("balance_after")).compareTo(od) == 0;
                if (!good) err.add("công nợ", w + ": sổ nợ phải có đúng 1 dòng số dư " + vnd(od) + ", đang có " + led.size() + " dòng");
            } else if (!led.isEmpty()) err.add("công nợ", w + " không nợ mà có dòng sổ nợ");
        }
        err.eq("công nợ", "TỔNG NỢ", vnd(oldDebt), vnd(newDebt));
        err.eq("công nợ", "số dòng sổ nợ", withDebt, ledger.size());

        // ---------- Hoa don
        Map<String, Map<String, Object>> ord = new HashMap<>();
        for (var r : jdbc.queryForList("SELECT id, code, status, customer_id, customer_name, customer_phone, staff_name, items_total, "
                + "shipping_fee, previous_debt, total_payable, paid_amount, debt_after, note, created_at, checked_out_at FROM orders"))
            ord.put((String) r.get("code"), r);
        err.eq("hóa đơn", "số hóa đơn", d.orders().size(), ord.size());
        Set<Integer> custIds = new HashSet<>();
        d.customers().forEach(c -> custIds.add(c.id()));
        int walkIn = 0;
        for (var o : d.orders()) {
            var n = ord.get(o.id());
            String g = "hóa đơn", w = o.id();
            if (n == null) { err.add(g, "thiếu " + w); continue; }
            if (!"PAID".equals(n.get("status"))) err.add(g, w + " trạng thái " + n.get("status"));
            err.eq(g, w + " ngày giờ", time(o.date()), time(n.get("created_at")));
            err.eq(g, w + " giờ chốt", time(o.date()), time(n.get("checked_out_at")));
            err.eq(g, w + " tên khách", Optional.ofNullable(norm(o.customer())).orElse("Khách lẻ"), n.get("customer_name"));
            err.eq(g, w + " SĐT", norm(o.phone()), n.get("customer_phone"));
            err.eq(g, w + " nhân viên", norm(o.staff()), n.get("staff_name"));
            BigDecimal bill = z(c2(o.totalBill())), ship = z(c2(o.ship()));
            err.eq(g, w + " tiền hàng", vnd(bill), vnd(n.get("items_total")));
            err.eq(g, w + " phí ship", vnd(ship), vnd(n.get("shipping_fee")));
            boolean regular = o.customerId() != null && custIds.contains(o.customerId());
            Object cid = n.get("customer_id");
            err.eq(g, w + " mã khách", regular ? o.customerId().longValue() : null, cid == null ? null : ((Number) cid).longValue());
            if (regular) { // dung nhu da in
                BigDecimal prev = z(c2(o.oldDebt()));
                BigDecimal total = c2(o.totalOrder()) != null ? c2(o.totalOrder()) : bill.add(ship).add(prev);
                BigDecimal after = z(c2(o.debtBack()));
                BigDecimal paid = c2(o.pay()) != null ? c2(o.pay()) : total.subtract(after);
                err.eq(g, w + " nợ cũ", vnd(prev), vnd(n.get("previous_debt")));
                err.eq(g, w + " tổng thanh toán", vnd(total), vnd(n.get("total_payable")));
                err.eq(g, w + " khách trả", vnd(paid), vnd(n.get("paid_amount")));
                err.eq(g, w + " còn nợ", vnd(after), vnd(n.get("debt_after")));
            } else { // khach le: tra du
                walkIn++;
                BigDecimal total = bill.add(ship), in = z(c2(o.pay()));
                err.eq(g, w + " (lẻ) nợ cũ", vnd(BigDecimal.ZERO), vnd(n.get("previous_debt")));
                err.eq(g, w + " (lẻ) tổng", vnd(total), vnd(n.get("total_payable")));
                err.eq(g, w + " (lẻ) khách trả", vnd(in.compareTo(total) > 0 ? in : total), vnd(n.get("paid_amount")));
                err.eq(g, w + " (lẻ) còn nợ", vnd(BigDecimal.ZERO), vnd(n.get("debt_after")));
            }
            String oldNote = norm(o.note()), newNote = (String) n.get("note");
            if (oldNote != null && (newNote == null || !newNote.startsWith(oldNote))) err.add(g, w + " ghi chú: cũ=" + oldNote + " mới=" + newNote);
        }

        // ---------- Dong hoa don: tung dong, dung thu tu
        Map<String, List<Map<String, Object>>> newItems = new LinkedHashMap<>();
        for (var r : jdbc.queryForList("SELECT o.code, i.product_id, i.product_name, i.unit, i.unit_price, i.quantity, i.sheets, i.factor, "
                + "i.total_quantity, i.line_total, i.cost_total FROM order_item i JOIN orders o ON o.id = i.order_id ORDER BY i.id"))
            newItems.computeIfAbsent((String) r.get("code"), k -> new ArrayList<>()).add(r);
        Set<String> codes = new HashSet<>();
        d.orders().forEach(o -> codes.add(o.id()));
        Set<Integer> prodIds = new HashSet<>();
        d.products().forEach(p -> prodIds.add(p.id()));
        Map<String, List<LegacyData.Bill>> oldItems = new LinkedHashMap<>();
        int dropped = 0, newCount = 0;
        for (var b : d.bills().stream().sorted(Comparator.comparing(LegacyData.Bill::id)).toList()) {
            if (!codes.contains(b.orderId())) { dropped++; continue; }
            oldItems.computeIfAbsent(b.orderId(), k -> new ArrayList<>()).add(b);
        }
        for (var l : newItems.values()) newCount += l.size();
        err.eq("dòng hóa đơn", "số dòng", d.bills().size() - dropped, newCount);
        BigDecimal sumOld = BigDecimal.ZERO, sumNew = BigDecimal.ZERO;
        for (var e : oldItems.entrySet()) {
            var olds = e.getValue();
            var news = newItems.getOrDefault(e.getKey(), List.of());
            if (olds.size() != news.size()) { err.add("dòng hóa đơn", e.getKey() + ": cũ " + olds.size() + " dòng, mới " + news.size()); continue; }
            for (int k = 0; k < olds.size(); k++) {
                var o = olds.get(k);
                var n = news.get(k);
                String g = "dòng hóa đơn", w = e.getKey() + " dòng " + (k + 1);
                Object pid = n.get("product_id");
                err.eq(g, w + " sản phẩm", prodIds.contains(o.productId()) ? o.productId().longValue() : null, pid == null ? null : ((Number) pid).longValue());
                err.eq(g, w + " tên", norm(o.productName()), n.get("product_name"));
                err.eq(g, w + " đơn vị", norm(o.unit()), n.get("unit"));
                err.eq(g, w + " số lượng", q3(o.amount()), q3(n.get("quantity")));
                err.eq(g, w + " số tấm", o.soTam() == null ? 0 : o.soTam(), ((Number) n.get("sheets")).intValue());
                err.eq(g, w + " hệ số", q3(o.heSo() == null ? "0" : o.heSo()), q3(n.get("factor")));
                err.eq(g, w + " tổng SL", q3(o.tongSoLuong()), q3(n.get("total_quantity")));
                err.eq(g, w + " đơn giá", vnd(c2(o.price())), vnd(n.get("unit_price")));
                err.eq(g, w + " thành tiền", vnd(c2(o.intoMoney())), vnd(n.get("line_total")));
                if (n.get("cost_total") != null) err.add(g, w + " có giá vốn (phải trống)");
                sumOld = sumOld.add(c2(o.intoMoney()));
                sumNew = sumNew.add((BigDecimal) n.get("line_total"));
            }
        }
        for (String code : newItems.keySet()) if (!oldItems.containsKey(code)) err.add("dòng hóa đơn", code + " có dòng mà bản cũ không có");
        err.eq("dòng hóa đơn", "TỔNG THÀNH TIỀN", vnd(sumOld), vnd(sumNew));

        // ---------- Doanh thu tung thang (tien hang)
        Map<String, BigDecimal> mOld = new TreeMap<>(), mNew = new TreeMap<>();
        for (var o : d.orders()) mOld.merge(o.date().substring(0, 7), z(c2(o.totalBill())), BigDecimal::add);
        for (var r : ord.values()) mNew.merge(((Timestamp) r.get("created_at")).toLocalDateTime().toString().substring(0, 7), (BigDecimal) r.get("items_total"), BigDecimal::add);
        for (var e : mOld.entrySet()) err.eq("doanh thu", "tháng " + e.getKey(), vnd(e.getValue()), vnd(mNew.get(e.getKey())));
        err.eq("doanh thu", "số tháng", mOld.size(), mNew.size());

        // ---------- So hoa don tiep theo: phai lon hon moi ma cu
        long maxCode = Math.max(d.invoiceCounter() == null ? 0 : d.invoiceCounter(), d.orders().stream()
                .mapToLong(o -> Long.parseLong("0" + o.id().replaceAll("\\D", ""))).max().orElse(0));
        long next = nextCode();
        err.eq("số hóa đơn", "số hóa đơn tiếp theo", maxCode + 1, next);

        // ---------- Bat thuong trong du lieu cu (da chuyen nguyen trang)
        List<String> odd = new ArrayList<>();
        for (var o : d.orders()) {
            BigDecimal bill = z(c2(o.totalBill()));
            BigDecimal lines = oldItems.getOrDefault(o.id(), List.of()).stream().map(b -> c2(b.intoMoney())).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
            if (lines.compareTo(bill) != 0)
                odd.add(o.id() + " (" + o.date().substring(0, 10) + "): tiền hàng trên hóa đơn " + vnd(bill) + " khác tổng các dòng " + vnd(lines));
            BigDecimal pay = c2(o.pay()), total = c2(o.totalOrder());
            if (pay != null && total != null && pay.compareTo(total.multiply(BigDecimal.TEN)) > 0 && pay.compareTo(new BigDecimal("1000000")) > 0)
                odd.add(o.id() + " (" + o.date().substring(0, 10) + ", " + o.customer() + "): khách trả " + vnd(pay) + " đ, tổng chỉ " + vnd(total) + " đ (có thể gõ nhầm)");
        }

        List<Line> summary = List.of(
                line("Loại hàng", d.classify().size() + "", catName.size() + " (gộp " + (d.classify().size() - catName.size()) + " loại trùng tên)", distinct.size() == catName.size()),
                line("Sản phẩm", d.products().size() + "", prod.size() + "", d.products().size() == prod.size()),
                line("Khách hàng", d.customers().size() + " (" + withDebt + " khách có nợ)", cust.size() + "", d.customers().size() == cust.size()),
                line("Tổng nợ", vnd(oldDebt) + " đ", vnd(newDebt) + " đ", oldDebt.compareTo(newDebt) == 0),
                line("Hóa đơn", d.orders().size() + "", ord.size() + " (" + walkIn + " HĐ khách lẻ: trả đủ)", d.orders().size() == ord.size()),
                line("Dòng hóa đơn", (d.bills().size() - dropped) + " (bỏ " + dropped + " dòng của HĐ đã xóa)", newCount + "", d.bills().size() - dropped == newCount),
                line("Tổng thành tiền", vnd(sumOld) + " đ", vnd(sumNew) + " đ", sumOld.compareTo(sumNew) == 0),
                line("Doanh thu", mOld.size() + " tháng", mNew.size() + " tháng, khớp từng tháng", mOld.equals(mNew)),
                line("Hóa đơn tiếp theo", "", String.format("HD%08d", next), next == maxCode + 1));
        return new Report(count[0] == 0, summary, odd, errors, count[0]);
    }

    private long nextCode() {
        // doc so tiep theo ma khong tieu so. KHONG thu-loi: loi SQL tren PostgreSQL lam hong ca transaction
        String db = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
        if ("H2".equalsIgnoreCase(db))
            return jdbc.queryForObject("SELECT BASE_VALUE FROM INFORMATION_SCHEMA.SEQUENCES WHERE LOWER(SEQUENCE_NAME) = 'order_code_seq'", Long.class);
        Map<String, Object> r = jdbc.queryForMap("SELECT last_value, is_called FROM order_code_seq");
        long last = ((Number) r.get("last_value")).longValue();
        return Boolean.TRUE.equals(r.get("is_called")) ? last + 1 : last;
    }

    private Map<Long, Map<String, Object>> rows(String sql) {
        Map<Long, Map<String, Object>> m = new HashMap<>();
        for (var r : jdbc.queryForList(sql)) m.put(((Number) r.get("id")).longValue(), r);
        return m;
    }

    private static Line line(String l, String a, String b, boolean ok) { return new Line(l, a, b, ok); }

    private static String norm(String s) {
        if (s == null) return null;
        String t = s.replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? null : t;
    }

    private static String low(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }

    /** so tien (chu) -> 2 so le, lam tron nua len */
    private static BigDecimal c2(String s) { return s == null || s.isBlank() ? null : new BigDecimal(s.trim()).setScale(2, RoundingMode.HALF_UP); }

    private static BigDecimal z(BigDecimal v) { return v == null ? BigDecimal.ZERO.setScale(2) : v; }

    private static BigDecimal q3(Object v) {
        if (v == null) return null;
        return new BigDecimal(v.toString().trim()).setScale(3, RoundingMode.HALF_UP);
    }

    private static String time(Object v) {
        if (v == null) return null;
        LocalDateTime t = v instanceof Timestamp ts ? ts.toLocalDateTime()
                : LocalDateTime.parse(v.toString().trim().replace(' ', 'T'));
        return t.withNano(t.getNano() / 1_000_000 * 1_000_000).toString();
    }

    private static String vnd(Object v) {
        if (v == null) return "-";
        DecimalFormat f = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(new Locale("vi", "VN")));
        return f.format(v instanceof BigDecimal b ? b : new BigDecimal(v.toString()));
    }
}

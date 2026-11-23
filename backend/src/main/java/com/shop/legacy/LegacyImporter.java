package com.shop.legacy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Nap du lieu phan mem cu vao database MOI. Chay trong transaction cua LegacyImportService (doi chieu lech -> huy het).
 *
 *  Loai hang : gop loai trung ten (bo khoang trang, khong phan biet hoa thuong) -> giu ID nho nhat.
 *  San pham  : giu ID; gia (chu) -> so; ten / don vi bo khoang trang thua; ton kho bat dau 0.
 *  Khach     : giu ID; no = Customer.Debt (so dung: app cu cho sua tay no nen lich su khong cong lai duoc)
 *              -> khach co no: 1 dong so no MANUAL "So du chuyen tu phan mem cu".
 *  Hoa don   : giu ma + ngay gio, PAID, KHONG ghi so no (no da chot o tren); so tien bat thuong de nguyen.
 *     - khach quen: dung nhu da in. Khach tra trong -> tong thanh toan - con no.
 *     - khach le  : tra du (nghiep vu: khach le khong no; app cu khong bat nhap tien khach dua): no cu 0,
 *                   tong = tien hang + ship, khach tra = max(so da nhap, tong), con no 0.
 *                   App cu ghi no cu tren HD le (khach da bi xoa) -> ghi lai vao ghi chu.
 *  Dong HD   : bo dong cua hoa don da xoa; san pham da xoa -> product_id trong, giu ten.
 *  So HD tiep: order_code_seq tiep tuc so cu (InvoiceCounter / ma lon nhat).
 */
@Component
public class LegacyImporter {
    public static final String OPENING_NOTE = "Số dư chuyển từ phần mềm cũ";
    static final String CREATED_BY = "Chuyển dữ liệu";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]");

    private final JdbcTemplate jdbc;

    public LegacyImporter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Stats(int categories, int mergedCategories, int products, int customers, int customersWithDebt,
                        int orders, int items, int droppedItems, long nextCode) {}

    public Stats load(LegacyData d, LocalDateTime now) {
        // ---- Loai hang
        Map<Integer, Integer> catOf = new HashMap<>();
        Map<String, Integer> byKey = new LinkedHashMap<>();
        List<Object[]> cats = new ArrayList<>();
        d.classify().stream().sorted(Comparator.comparing(LegacyData.Classify::id)).forEach(c -> {
            String k = key(c.name());
            if (!byKey.containsKey(k)) { byKey.put(k, c.id()); cats.add(new Object[]{c.id(), clean(c.name())}); }
            catOf.put(c.id(), byKey.get(k));
        });
        jdbc.batchUpdate("INSERT INTO category (id, name) VALUES (?, ?)", cats);

        // ---- San pham + ton kho
        Set<Integer> productIds = new HashSet<>();
        List<Object[]> prods = new ArrayList<>();
        for (var p : d.products()) {
            productIds.add(p.id());
            prods.add(new Object[]{p.id(), catOf.get(p.classifyId()), clean(p.name()), clean(p.unit()), money(p.price()),
                    ts(p.createdAt(), now)});
        }
        jdbc.batchUpdate("INSERT INTO product (id, category_id, name, unit, price, active, min_stock, created_at) "
                + "VALUES (?, ?, ?, ?, ?, TRUE, 0, ?)", prods);
        jdbc.batchUpdate("INSERT INTO stock_balance (product_id) VALUES (?)",
                d.products().stream().map(p -> new Object[]{p.id()}).toList());

        // ---- Khach hang + so du no
        Set<Integer> customerIds = new HashSet<>();
        List<Object[]> custs = new ArrayList<>(), ledger = new ArrayList<>();
        long ledgerId = 0;
        for (var c : d.customers()) {
            customerIds.add(c.id());
            BigDecimal debt = Optional.ofNullable(money(c.debt())).orElse(BigDecimal.ZERO.setScale(2));
            custs.add(new Object[]{c.id(), clean(c.name()), clean(c.address()), clean(c.phone()), debt, ts(c.createdAt(), now)});
            if (debt.signum() != 0)
                ledger.add(new Object[]{++ledgerId, c.id(), debt, debt, OPENING_NOTE, CREATED_BY, Timestamp.valueOf(now)});
        }
        jdbc.batchUpdate("INSERT INTO customer (id, name, address, phone, debt, active, version, created_at) "
                + "VALUES (?, ?, ?, ?, ?, TRUE, 0, ?)", custs);

        // ---- Hoa don
        Map<String, Long> orderId = new HashMap<>();
        List<Object[]> ords = new ArrayList<>();
        long oid = 0;
        for (var o : d.orders()) {
            orderId.put(o.id(), ++oid);
            BigDecimal bill = nz(money(o.totalBill())), ship = nz(money(o.ship()));
            BigDecimal prev, total, paid, after;
            String note = clean(o.note());
            boolean regular = o.customerId() != null && customerIds.contains(o.customerId());
            if (regular) {
                prev = nz(money(o.oldDebt()));
                total = Optional.ofNullable(money(o.totalOrder())).orElse(bill.add(ship).add(prev));
                after = nz(money(o.debtBack()));
                paid = Optional.ofNullable(money(o.pay())).orElse(total.subtract(after));
            } else {
                prev = BigDecimal.ZERO.setScale(2);
                total = bill.add(ship);
                paid = nz(money(o.pay())).max(total);
                after = BigDecimal.ZERO.setScale(2);
                BigDecimal old = nz(money(o.oldDebt()));
                if (old.signum() != 0) {
                    String extra = "Phần mềm cũ ghi nợ cũ " + old.stripTrailingZeros().toPlainString() + " đ";
                    note = note == null ? extra : note + ". " + extra;
                }
            }
            Timestamp at = ts(o.date(), now);
            ords.add(new Object[]{oid, o.id(), regular ? o.customerId() : null, Optional.ofNullable(clean(o.customer())).orElse("Khách lẻ"),
                    clean(o.address()), clean(o.phone()), clean(o.staff()), bill, ship, prev, total, paid, after, note, at, at});
        }
        jdbc.batchUpdate("INSERT INTO orders (id, code, status, customer_id, customer_name, customer_address, customer_phone, "
                + "staff_name, items_total, shipping_fee, previous_debt, total_payable, paid_amount, debt_after, note, created_at, "
                + "checked_out_at, refund_amount) VALUES (?, ?, 'PAID', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)", ords);

        // ---- Dong hoa don (theo thu tu cu)
        List<Object[]> items = new ArrayList<>();
        long iid = 0;
        int dropped = 0;
        for (var b : d.bills().stream().sorted(Comparator.comparing(LegacyData.Bill::id)).toList()) {
            Long o = orderId.get(b.orderId());
            if (o == null) { dropped++; continue; }
            items.add(new Object[]{++iid, o, productIds.contains(b.productId()) ? b.productId() : null,
                    Optional.ofNullable(clean(b.productName())).orElse("(không rõ tên)"), clean(b.unit()), money(b.price()),
                    num(b.amount()), b.soTam() == null ? 0 : b.soTam(), Optional.ofNullable(num(b.heSo())).orElse(BigDecimal.ZERO),
                    num(b.tongSoLuong()), money(b.intoMoney())});
        }
        jdbc.batchUpdate("INSERT INTO order_item (id, order_id, product_id, product_name, unit, unit_price, quantity, sheets, factor, "
                + "total_quantity, line_total) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", items);

        jdbc.batchUpdate("INSERT INTO debt_ledger (id, customer_id, type, amount, balance_after, note, created_by, created_at) "
                + "VALUES (?, ?, 'MANUAL', ?, ?, ?, ?, ?)", ledger);

        // ---- ID tu tang tiep tuc sau so lon nhat; so hoa don tiep tuc so cu (cu phap chung PostgreSQL + H2)
        for (String t : List.of("category", "product", "customer", "orders", "order_item", "debt_ledger")) {
            Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + t, Long.class);
            jdbc.execute("ALTER TABLE " + t + " ALTER COLUMN id RESTART WITH " + (max + 1));
        }
        long lastCode = Math.max(d.invoiceCounter() == null ? 0 : d.invoiceCounter(), d.orders().stream()
                .mapToLong(o -> { String n = o.id().replaceAll("\\D", ""); return n.isEmpty() ? 0 : Long.parseLong(n); }).max().orElse(0));
        jdbc.execute("ALTER SEQUENCE order_code_seq RESTART WITH " + (lastCode + 1));

        return new Stats(cats.size(), d.classify().size() - cats.size(), prods.size(), custs.size(), ledger.size(),
                ords.size(), items.size(), dropped, lastCode + 1);
    }

    static String clean(String s) {
        if (s == null) return null;
        String t = s.replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? null : t;
    }

    static String key(String s) { return Optional.ofNullable(clean(s)).orElse("").toLowerCase(Locale.ROOT); }

    static BigDecimal num(String s) { return s == null || s.isBlank() ? null : new BigDecimal(s.trim()); }

    static BigDecimal money(String s) { BigDecimal v = num(s); return v == null ? null : v.setScale(2, RoundingMode.HALF_UP); }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO.setScale(2) : v; }

    static Timestamp ts(String s, LocalDateTime fallback) {
        return Timestamp.valueOf(s == null || s.isBlank() ? fallback : LocalDateTime.parse(s.trim(), TS));
    }
}

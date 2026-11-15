package com.shop.report;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Thay man ThongKe cu. Cong thuc giu theo app cu, chi khac:
 * - chi tinh hoa don da chot (app cu tinh ca dong hang cua hoa don nhap chua thanh toan);
 * - "thang nay"/"khach moi thang nay" loc ca nam (app cu chi loc MONTH(), dinh ca thang cung ten cua nam truoc).
 * Doanh thu = tong thanh tien dong hang (Bill.IntoMoney), khong gom phi ship, khong gom no cu.
 */
@RestController
@RequestMapping("/api/reports")
@Transactional(readOnly = true)
public class ReportController {
    private final EntityManager em;

    public ReportController(EntityManager em) { this.em = em; }

    public record DayRevenue(LocalDate day, long orders, BigDecimal revenue, BigDecimal collected) {}
    public record MonthRevenue(int month, BigDecimal revenue) {}
    public record TopProduct(Long productId, String productName, BigDecimal totalQuantity, BigDecimal revenue) {}
    public record TopCustomer(Long customerId, String customerName, long orders, BigDecimal totalAmount) {}
    public record CategoryStat(String category, long productCount, BigDecimal totalQuantity, BigDecimal revenue) {}
    public record CustomerStat(long newCustomers, long returningCustomers, long totalCustomers, double returningRate) {}
    public record Period(BigDecimal current, BigDecimal previous) {}
    public record Overview(Period day, Period week, Period month, Period year,
                           long ordersThisMonth, BigDecimal avgOrderThisMonth,
                           long newCustomersThisMonth, double returningRate,
                           BigDecimal totalDebt, long customersInDebt) {}

    private static final String PAID_ITEMS = """
            from order_item i join orders o on o.id = i.order_id
            where o.status = 'PAID' and o.checked_out_at >= :f and o.checked_out_at < :t
            """;

    // ---------------- che do mac dinh (dau trang ThongKe cu) ----------------
    @GetMapping("/overview")
    public Overview overview() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate yearStart = today.withDayOfYear(1);

        Period day = new Period(revenue(today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                revenue(today.minusDays(1).atStartOfDay(), today.atStartOfDay()));
        // App cu: tuan = 7*24h gan nhat tinh tu thoi diem hien tai; tuan truoc = tu -14 ngay den -7 ngay
        Period week = new Period(revenue(now.minusDays(7), now.plusSeconds(1)), revenue(now.minusDays(14), now.minusDays(7)));
        Period month = new Period(revenue(monthStart.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                revenue(monthStart.minusMonths(1).atStartOfDay(), monthStart.atStartOfDay()));
        Period year = new Period(revenue(yearStart.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                revenue(yearStart.minusYears(1).atStartOfDay(), yearStart.atStartOfDay()));

        LocalDateTime mf = monthStart.atStartOfDay(), mt = today.plusDays(1).atStartOfDay();
        long orders = num("select count(*) from orders where status = 'PAID' and checked_out_at >= :f and checked_out_at < :t", mf, mt).longValue();
        // App cu: AVG(TotalMoneyOrder) = trung binh "tong thanh toan" (tien hang + no cu + ship)
        BigDecimal avg = bd(num("select coalesce(avg(total_payable),0) from orders where status = 'PAID' and checked_out_at >= :f and checked_out_at < :t", mf, mt))
                .setScale(0, RoundingMode.HALF_UP);
        long newCustomers = num("select count(*) from customer where created_at >= :f and created_at < :t", mf, mt).longValue();
        CustomerStat cs = customerStat(null, null);
        Object[] debt = (Object[]) em.createNativeQuery(
                "select coalesce(sum(debt),0), count(*) from customer where active = true and debt > 0").getSingleResult();

        return new Overview(day, week, month, year, orders, avg, newCustomers, cs.returningRate(),
                bd(debt[0]), ((Number) debt[1]).longValue());
    }

    // ---------------- che do chon khoang ngay ----------------
    @GetMapping("/revenue")
    @SuppressWarnings("unchecked")
    public List<DayRevenue> revenue(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<Object[]> rows = range(em.createNativeQuery("""
                select cast(checked_out_at as date) d, count(*), coalesce(sum(items_total),0), coalesce(sum(least(paid_amount, total_payable)),0)
                from orders
                where status = 'PAID' and checked_out_at >= :f and checked_out_at < :t
                group by cast(checked_out_at as date) order by d
                """), from, to).getResultList();
        return rows.stream().map(r -> new DayRevenue(toDate(r[0]), ((Number) r[1]).longValue(), bd(r[2]), bd(r[3]))).toList();
    }

    /** Bieu do doanh thu 12 thang cua mot nam (app cu: thang chua co du lieu = 0). */
    @GetMapping("/revenue-by-month")
    @SuppressWarnings("unchecked")
    public List<MonthRevenue> byMonth(@RequestParam(required = false) Integer year) {
        int y = year == null ? LocalDate.now().getYear() : year;
        List<Object[]> rows = range(em.createNativeQuery("""
                select extract(month from checked_out_at) m, coalesce(sum(items_total),0)
                from orders where status = 'PAID' and checked_out_at >= :f and checked_out_at < :t
                group by extract(month from checked_out_at)
                """), LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)).getResultList();
        BigDecimal[] m = new BigDecimal[12];
        java.util.Arrays.fill(m, BigDecimal.ZERO);
        rows.forEach(r -> m[((Number) r[0]).intValue() - 1] = bd(r[1]));
        List<MonthRevenue> out = new ArrayList<>();
        for (int i = 0; i < 12; i++) out.add(new MonthRevenue(i + 1, m[i]));
        return out;
    }

    /** Top 10 san pham: sort=quantity ("So luong ban") hoac revenue (doanh thu). */
    @GetMapping("/top-products")
    @SuppressWarnings("unchecked")
    public List<TopProduct> top(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(defaultValue = "revenue") String sort) {
        String order = "quantity".equals(sort) ? "q desc" : "s desc";
        List<Object[]> rows = range(em.createNativeQuery(
                "select i.product_id, i.product_name, sum(i.total_quantity) q, sum(i.line_total) s " + PAID_ITEMS
                        + " group by i.product_id, i.product_name order by " + order + " limit 10"), from, to).getResultList();
        return rows.stream().map(r -> new TopProduct(r[0] == null ? null : ((Number) r[0]).longValue(), (String) r[1], bd(r[2]), bd(r[3]))).toList();
    }

    /** Top 10 khach than thiet: sort=orders ("So don hang") hoac amount (tong TotalMoneyOrder nhu app cu). */
    @GetMapping("/top-customers")
    @SuppressWarnings("unchecked")
    public List<TopCustomer> topCustomers(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(defaultValue = "orders") String sort) {
        String order = "amount".equals(sort) ? "a desc, n desc" : "n desc, a desc";
        List<Object[]> rows = range(em.createNativeQuery("""
                select c.id, c.name, count(o.id) n, coalesce(sum(o.total_payable),0) a
                from customer c join orders o on o.customer_id = c.id
                where o.status = 'PAID' and o.checked_out_at >= :f and o.checked_out_at < :t
                group by c.id, c.name order by """ + " " + order + " limit 10"), from, to).getResultList();
        return rows.stream().map(r -> new TopCustomer(((Number) r[0]).longValue(), (String) r[1],
                ((Number) r[2]).longValue(), bd(r[3]))).toList();
    }

    /** Phan bo theo loai hang: so mat hang trong loai, tong SL ban, doanh thu (bieu do tron cu). */
    @GetMapping("/categories")
    @SuppressWarnings("unchecked")
    public List<CategoryStat> categories(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<Object[]> rows = range(em.createNativeQuery("""
                select cat.name, count(distinct p.id),
                       coalesce(sum(s.q),0), coalesce(sum(s.r),0)
                from category cat
                left join product p on p.category_id = cat.id
                left join (select i.product_id pid, sum(i.total_quantity) q, sum(i.line_total) r
                           from order_item i join orders o on o.id = i.order_id
                           where o.status = 'PAID' and o.checked_out_at >= :f and o.checked_out_at < :t
                           group by i.product_id) s on s.pid = p.id
                group by cat.name order by 4 desc
                """), from, to).getResultList();
        return rows.stream().map(r -> new CategoryStat((String) r[0], ((Number) r[1]).longValue(), bd(r[2]), bd(r[3]))).toList();
    }

    @GetMapping("/customers")
    public CustomerStat customers(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return customerStat(from, to);
    }

    /**
     * App cu: khach quay lai = khach co tu 2 hoa don tro len (tren toan bo lich su);
     * neu chon khoang ngay thi chi dem khach do co hoa don trong khoang. Ty le = so do / tong so khach.
     */
    private CustomerStat customerStat(LocalDate from, LocalDate to) {
        LocalDateTime f = from == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : from.atStartOfDay();
        LocalDateTime t = to == null ? LocalDateTime.of(9999, 1, 1, 0, 0) : to.plusDays(1).atStartOfDay();
        long newC = from == null ? 0 : num("select count(*) from customer where created_at >= :f and created_at < :t", f, t).longValue();
        long returning = num("""
                select count(distinct o.customer_id) from orders o
                where o.status = 'PAID' and o.checked_out_at >= :f and o.checked_out_at < :t
                  and o.customer_id in (select customer_id from orders where status = 'PAID' and customer_id is not null
                                        group by customer_id having count(*) > 1)
                """, f, t).longValue();
        long total = ((Number) em.createNativeQuery("select count(*) from customer where active = true").getSingleResult()).longValue();
        double rate = total == 0 ? 0 : Math.round(returning * 1000.0 / total) / 10.0;
        return new CustomerStat(newC, returning, total, rate);
    }

    // ---------------- helper ----------------
    private Query range(Query q, LocalDate from, LocalDate to) {
        return q.setParameter("f", from.atStartOfDay()).setParameter("t", to.plusDays(1).atStartOfDay());
    }

    private BigDecimal revenue(LocalDateTime from, LocalDateTime to) {
        return bd(num("select coalesce(sum(items_total),0) from orders where status = 'PAID' and checked_out_at >= :f and checked_out_at < :t", from, to));
    }

    private Number num(String sql, LocalDateTime from, LocalDateTime to) {
        Object r = em.createNativeQuery(sql).setParameter("f", from).setParameter("t", to).getSingleResult();
        return r instanceof Number n ? n : new BigDecimal(r.toString());
    }

    private static BigDecimal bd(Object o) {
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    private static LocalDate toDate(Object o) {
        if (o instanceof Date d) return d.toLocalDate();
        if (o instanceof LocalDate l) return l;
        return LocalDate.parse(o.toString().substring(0, 10));
    }
}

package com.shop.report;

import jakarta.persistence.EntityManager;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final EntityManager em;

    public ReportController(EntityManager em) { this.em = em; }

    /** revenue = doanh thu ban ra (tien hang), collected = tien thuc thu tren cac hoa don. Chi tinh hoa don PAID. */
    public record DayRevenue(LocalDate day, long orders, BigDecimal revenue, BigDecimal collected) {}
    public record TopProduct(String productName, BigDecimal totalQuantity, BigDecimal revenue) {}

    @GetMapping("/revenue")
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<DayRevenue> revenue(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<Object[]> rows = em.createNativeQuery("""
                select cast(checked_out_at as date) d, count(*), coalesce(sum(items_total),0), coalesce(sum(paid_amount),0)
                from orders
                where status = 'PAID' and checked_out_at >= :from and checked_out_at < :to
                group by cast(checked_out_at as date) order by d
                """)
                .setParameter("from", from.atStartOfDay())
                .setParameter("to", to.plusDays(1).atStartOfDay())
                .getResultList();
        return rows.stream().map(r -> new DayRevenue(toDate(r[0]), ((Number) r[1]).longValue(),
                bd(r[2]), bd(r[3]))).toList();
    }

    @GetMapping("/top-products")
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<TopProduct> top(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<Object[]> rows = em.createNativeQuery("""
                select i.product_name, sum(i.total_quantity), sum(i.line_total) s
                from order_item i join orders o on o.id = i.order_id
                where o.status = 'PAID' and o.checked_out_at >= :from and o.checked_out_at < :to
                group by i.product_name order by s desc limit 10
                """)
                .setParameter("from", from.atStartOfDay())
                .setParameter("to", to.plusDays(1).atStartOfDay())
                .getResultList();
        return rows.stream().map(r -> new TopProduct((String) r[0], bd(r[1]), bd(r[2]))).toList();
    }

    /** Thay man ThongKe cu: doanh thu ngay/7 ngay/thang/nam so voi ky truoc, khach moi, ty le quay lai, tong no. */
    public record Period(BigDecimal current, BigDecimal previous) {}
    public record Overview(Period day, Period week, Period month, Period year,
                           long ordersThisMonth, BigDecimal avgOrderThisMonth,
                           long newCustomersThisMonth, double returningRate,
                           BigDecimal totalDebt, long customersInDebt) {}

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public Overview overview() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate yearStart = today.withDayOfYear(1);

        Period day = new Period(revenue(today, tomorrow), revenue(today.minusDays(1), today));
        Period week = new Period(revenue(today.minusDays(6), tomorrow), revenue(today.minusDays(13), today.minusDays(6)));
        Period month = new Period(revenue(monthStart, tomorrow), revenue(monthStart.minusMonths(1), monthStart));
        Period year = new Period(revenue(yearStart, tomorrow), revenue(yearStart.minusYears(1), yearStart));

        long orders = num("select count(*) from orders where status = 'PAID' and checked_out_at >= :f and checked_out_at < :t",
                monthStart, tomorrow).longValue();
        BigDecimal avg = orders == 0 ? BigDecimal.ZERO
                : month.current().divide(BigDecimal.valueOf(orders), 0, java.math.RoundingMode.HALF_UP);
        long newCustomers = num("select count(*) from customer where created_at >= :f and created_at < :t",
                monthStart, tomorrow).longValue();
        long buyers = num("""
                select count(distinct customer_id) from orders
                where status = 'PAID' and customer_id is not null and checked_out_at >= :f and checked_out_at < :t
                """, monthStart, tomorrow).longValue();
        long returning = num("""
                select count(distinct o.customer_id) from orders o
                where o.status = 'PAID' and o.customer_id is not null and o.checked_out_at >= :f and o.checked_out_at < :t
                  and exists (select 1 from orders p where p.customer_id = o.customer_id
                              and p.status = 'PAID' and p.checked_out_at < :f)
                """, monthStart, tomorrow).longValue();
        Object[] debt = (Object[]) em.createNativeQuery(
                "select coalesce(sum(debt),0), count(*) from customer where active = true and debt > 0").getSingleResult();

        return new Overview(day, week, month, year, orders, avg, newCustomers,
                buyers == 0 ? 0 : Math.round(returning * 1000.0 / buyers) / 10.0,
                bd(debt[0]), ((Number) debt[1]).longValue());
    }

    private BigDecimal revenue(LocalDate from, LocalDate to) {
        return bd(num("select coalesce(sum(items_total),0) from orders where status = 'PAID' and checked_out_at >= :f and checked_out_at < :t",
                from, to));
    }

    private Number num(String sql, LocalDate from, LocalDate to) {
        Object r = em.createNativeQuery(sql)
                .setParameter("f", from.atStartOfDay())
                .setParameter("t", to.atStartOfDay())
                .getSingleResult();
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

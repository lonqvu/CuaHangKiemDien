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

    private static BigDecimal bd(Object o) {
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    private static LocalDate toDate(Object o) {
        if (o instanceof Date d) return d.toLocalDate();
        if (o instanceof LocalDate l) return l;
        return LocalDate.parse(o.toString().substring(0, 10));
    }
}

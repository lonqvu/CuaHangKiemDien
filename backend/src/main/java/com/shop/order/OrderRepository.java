package com.shop.order;

import com.shop.common.Fold;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /*
     * Dieu kien chung cho danh sach hoa don:
     *  - ngay = ngay chot (hoa don nhap chua chot: ngay tao), giong cach man Thong ke tinh
     *  - q: tim theo ma, ten/SDT khach, nhan vien, ghi chu ("" = bo qua)
     *  - customerId = 0: moi khach
     */
    // q: tim khong dau (Fold) - "tuan" thay "Anh Tuấn"
    String WHERE = " coalesce(o.checkedOutAt, o.createdAt) >= :from and coalesce(o.checkedOutAt, o.createdAt) < :to"
            + " and (:customerId = 0 or o.customerId = :customerId)"
            + " and (:q = '' or lower(o.code) like :q or " + Fold.L + "o.customerName" + Fold.R + " or o.customerPhone like :q"
            + " or " + Fold.L + "o.staffName" + Fold.R + " or " + Fold.L + "o.note" + Fold.R + ") ";

    @Query(value = "select o from SalesOrder o where o.status in :statuses and " + WHERE,
           countQuery = "select count(o) from SalesOrder o where o.status in :statuses and " + WHERE)
    Page<Order> page(@Param("statuses") List<Order.Status> statuses, @Param("from") LocalDateTime from,
                     @Param("to") LocalDateTime to, @Param("q") String q, @Param("customerId") long customerId, Pageable page);

    /** [so hoa don, tien hang, tien thuc thu (khong tinh tien thua tra khach), con no] tren toan bo ket qua loc. */
    @Query("""
            select count(o), coalesce(sum(o.itemsTotal), 0),
                   coalesce(sum(case when o.paidAmount < o.totalPayable then o.paidAmount else o.totalPayable end), 0),
                   coalesce(sum(o.debtAfter), 0)
            from SalesOrder o where o.status in :statuses and
            """ + WHERE)
    List<Object[]> summary(@Param("statuses") List<Order.Status> statuses, @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to, @Param("q") String q, @Param("customerId") long customerId);

    /** So hoa don theo trang thai, cung bo loc (de hien so tren cac tab). */
    @Query("select o.status, count(o) from SalesOrder o where " + WHERE + " group by o.status")
    List<Object[]> countByStatus(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                 @Param("q") String q, @Param("customerId") long customerId);

    /** So dong hang da ban (hoa don da chot) theo san pham: de xep mat hang ban nhieu len dau khi tim. */
    @Query("select i.productId, count(i) from SalesOrder o join o.items i where o.status = :status group by i.productId")
    List<Object[]> countLinesByProduct(@Param("status") Order.Status status);

    /** [productId, so lan mua, lan mua gan nhat] cua MOT khach (hoa don da chot): xep hang khach hay mua len dau khi ban. */
    @Query("select i.productId, count(i), max(o.checkedOutAt) from SalesOrder o join o.items i where o.status = :status and o.customerId = :c group by i.productId")
    List<Object[]> purchasesOfCustomer(@Param("status") Order.Status status, @Param("c") Long customerId);

    @Query(value = "select nextval('order_code_seq')", nativeQuery = true)
    long nextCode();
}

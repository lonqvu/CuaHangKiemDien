package com.shop.stock;

import com.shop.common.Fold;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {
    /** Ngay = ngay ghi so (nhap: ngay tao). q: ma phieu, ten NCC, ghi chu. supplierId = 0: moi NCC. */
    String WHERE = " coalesce(r.postedAt, r.createdAt) >= :from and coalesce(r.postedAt, r.createdAt) < :to"
            + " and (:supplierId = 0 or r.supplierId = :supplierId)"
            + " and (:q = '' or lower(r.code) like :q or " + Fold.L + "r.supplierName" + Fold.R + " or " + Fold.L + "r.note" + Fold.R + ") ";

    @Query(value = "select r from GoodsReceipt r where r.status in :statuses and " + WHERE,
           countQuery = "select count(r) from GoodsReceipt r where r.status in :statuses and " + WHERE)
    Page<GoodsReceipt> page(@Param("statuses") List<GoodsReceipt.Status> statuses, @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to, @Param("q") String q, @Param("supplierId") long supplierId, Pageable page);

    @Query("select count(r), coalesce(sum(r.total), 0), coalesce(sum(r.paidAmount), 0) from GoodsReceipt r where r.status in :statuses and " + WHERE)
    List<Object[]> summary(@Param("statuses") List<GoodsReceipt.Status> statuses, @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to, @Param("q") String q, @Param("supplierId") long supplierId);

    @Query("select r.status, count(r) from GoodsReceipt r where " + WHERE + " group by r.status")
    List<Object[]> countByStatus(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                 @Param("q") String q, @Param("supplierId") long supplierId);

    @Query(value = "select nextval('receipt_code_seq')", nativeQuery = true)
    long nextCode();
}

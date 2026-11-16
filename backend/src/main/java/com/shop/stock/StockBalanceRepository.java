package com.shop.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {
    /** Khoa dong so du (SELECT ... FOR UPDATE): 2 quay cung ban 1 mat hang thi may sau cho may truoc xong. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from StockBalance b where b.productId = :id")
    Optional<StockBalance> lock(@Param("id") Long productId);

    List<StockBalance> findByProductIdIn(Collection<Long> ids);

    /**
     * Danh sach ton kho: [StockBalance, Product, ten loai]. filter: all | low (duoi muc toi thieu) | negative (am kho) | out (het hang, <= 0).
     * categoryId = 0: moi loai. q: mau LIKE ha chu thuong ("" = bo qua).
     */
    String WHERE = """
            p.active = true
              and (:categoryId = 0 or c.id = :categoryId)
              and (:q = '' or lower(p.name) like :q)
              and (:filter = 'all'
                   or (:filter = 'low' and p.minStock > 0 and b.onHand <= p.minStock)
                   or (:filter = 'negative' and b.onHand < 0)
                   or (:filter = 'out' and b.onHand <= 0))
            """;

    @Query(value = "select b, p, c.name from StockBalance b join Product p on p.id = b.productId left join p.category c where " + WHERE,
           countQuery = "select count(b) from StockBalance b join Product p on p.id = b.productId left join p.category c where " + WHERE)
    org.springframework.data.domain.Page<Object[]> stockPage(@Param("q") String q, @Param("categoryId") long categoryId,
                                                             @Param("filter") String filter, org.springframework.data.domain.Pageable page);

    /** [so mat hang, gia tri ton (chi phan duong), so mat hang duoi muc toi thieu, so mat hang am kho] - toan bo hang dang ban. */
    @Query("""
            select count(b),
                   coalesce(sum(case when b.onHand > 0 then b.onHand * b.avgCost else 0 end), 0),
                   sum(case when p.minStock > 0 and b.onHand <= p.minStock then 1 else 0 end),
                   sum(case when b.onHand < 0 then 1 else 0 end)
            from StockBalance b join Product p on p.id = b.productId where p.active = true
            """)
    List<Object[]> stockSummary();
}

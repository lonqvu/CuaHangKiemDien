package com.shop.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockCountRepository extends JpaRepository<StockCount, Long> {
    @Query(value = "select c from StockCount c where c.status = :status and (:q = '' or lower(c.code) like :q or lower(c.note) like :q)",
           countQuery = "select count(c) from StockCount c where c.status = :status and (:q = '' or lower(c.code) like :q or lower(c.note) like :q)")
    Page<StockCount> page(@Param("status") StockCount.Status status, @Param("q") String q, Pageable page);

    long countByStatus(StockCount.Status status);

    @Query(value = "select nextval('count_code_seq')", nativeQuery = true)
    long nextCode();
}

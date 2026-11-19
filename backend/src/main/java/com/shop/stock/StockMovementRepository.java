package com.shop.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    Page<StockMovement> findByProductIdOrderByIdDesc(Long productId, Pageable page);

    List<StockMovement> findByRefTypeAndRefIdOrderByIdAsc(String refType, Long refId);

    List<StockMovement> findByRefTypeAndRefIdAndTypeOrderByIdAsc(String refType, Long refId, StockMovement.Type type);

    @Query("select coalesce(sum(m.qty), 0) from StockMovement m where m.productId = :id")
    BigDecimal sumQty(@Param("id") Long productId);
}

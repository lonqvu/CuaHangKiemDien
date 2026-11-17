package com.shop.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductUnitRepository extends JpaRepository<ProductUnit, Long> {
    List<ProductUnit> findByProductIdOrderByUnit(Long productId);

    List<ProductUnit> findByProductIdIn(Collection<Long> productIds);

    @Modifying
    @Query("delete from ProductUnit u where u.productId = :id")
    void deleteAllOfProduct(@Param("id") Long productId);
}

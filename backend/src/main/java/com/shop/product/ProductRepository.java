package com.shop.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByActiveTrueAndNameContainingIgnoreCaseOrderByName(String name);

    boolean existsByCategoryIdAndActiveTrue(Long categoryId);

    @Modifying
    @Query("update Product p set p.category = null where p.category.id = :id")
    void clearCategory(@Param("id") Long id);
}

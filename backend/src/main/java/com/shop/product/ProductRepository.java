package com.shop.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByActiveTrueAndNameContainingIgnoreCaseOrderByName(String name);

    /** categoryId = 0: moi loai; -1: chua phan loai. q: mau LIKE ha chu thuong ("" = bo qua). */
    String WHERE = """
            p.active = :active
              and (:categoryId = 0 or (:categoryId = -1 and c is null) or c.id = :categoryId)
              and (:q = '' or lower(p.name) like :q)
            """;

    @Query(value = "select p from Product p left join fetch p.category c where " + WHERE,
           countQuery = "select count(p) from Product p left join p.category c where " + WHERE)
    org.springframework.data.domain.Page<Product> page(@Param("q") String q, @Param("categoryId") long categoryId,
                                                       @Param("active") boolean active, org.springframework.data.domain.Pageable page);

    /** [id loai, ten loai, so san pham dang ban] - ca loai chua co san pham. */
    @Query("""
            select c.id, c.name, count(p) from Category c left join Product p on p.category = c and p.active = true
            group by c.id, c.name order by c.name
            """)
    List<Object[]> categoryCounts();

    boolean existsByCategoryIdAndActiveTrue(Long categoryId);

    @Modifying
    @Query("update Product p set p.category = null where p.category.id = :id")
    void clearCategory(@Param("id") Long id);
}

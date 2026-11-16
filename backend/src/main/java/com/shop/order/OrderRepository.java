package com.shop.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("""
            select o from SalesOrder o
            where o.status = :status
              and o.createdAt >= :from and o.createdAt < :to
            order by o.createdAt desc, o.id desc
            """)
    List<Order> search(@Param("status") Order.Status status, @Param("from") java.time.LocalDateTime from,
                       @Param("to") java.time.LocalDateTime to, org.springframework.data.domain.Pageable page);

    /** So dong hang da ban (hoa don da chot) theo san pham: de xep mat hang ban nhieu len dau khi tim. */
    @Query("select i.productId, count(i) from SalesOrder o join o.items i where o.status = :status group by i.productId")
    List<Object[]> countLinesByProduct(@Param("status") Order.Status status);

    @Query(value = "select nextval('order_code_seq')", nativeQuery = true)
    long nextCode();
}

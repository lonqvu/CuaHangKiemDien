package com.shop.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findTop100ByStatusOrderByIdDesc(Order.Status status);

    @Query(value = "select nextval('order_code_seq')", nativeQuery = true)
    long nextCode();
}

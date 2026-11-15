package com.shop.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query("""
            select c from Customer c
            where c.active = true
              and (lower(c.name) like lower(concat('%', :q, '%')) or c.phone like concat('%', :q, '%'))
            order by c.name
            """)
    List<Customer> search(@Param("q") String q);
}

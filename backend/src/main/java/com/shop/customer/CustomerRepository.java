package com.shop.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByActiveTrueOrderByName();

    boolean existsByActiveTrueAndNameIgnoreCase(String name);

    boolean existsByActiveTrueAndNameIgnoreCaseAndIdNot(String name, Long id);
}

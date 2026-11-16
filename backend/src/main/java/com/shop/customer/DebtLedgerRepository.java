package com.shop.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebtLedgerRepository extends JpaRepository<DebtLedger, Long> {
    List<DebtLedger> findByCustomerIdOrderByIdDesc(Long customerId);

    Page<DebtLedger> findByCustomerIdOrderByIdDesc(Long customerId, Pageable page);
}

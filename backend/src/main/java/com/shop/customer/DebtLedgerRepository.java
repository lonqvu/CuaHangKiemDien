package com.shop.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebtLedgerRepository extends JpaRepository<DebtLedger, Long> {
    List<DebtLedger> findByCustomerIdOrderByIdDesc(Long customerId);
}

package com.shop.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebtLedgerRepository extends JpaRepository<DebtLedger, Long> {
    List<DebtLedger> findByCustomerIdOrderByIdDesc(Long customerId);

    List<DebtLedger> findByCustomerIdOrderByIdAsc(Long customerId);

    boolean existsByReversalOf(Long ledgerId);

    @org.springframework.data.jpa.repository.Query(value = "select nextval('receipt_no_seq')", nativeQuery = true)
    long nextReceiptNo();
}

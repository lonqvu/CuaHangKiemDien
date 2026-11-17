package com.shop.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebtLedgerRepository extends JpaRepository<DebtLedger, Long> {
    List<DebtLedger> findByCustomerIdOrderByIdDesc(Long customerId);

    List<DebtLedger> findByCustomerIdOrderByIdAsc(Long customerId);

    boolean existsByReversalOf(Long ledgerId);

    @org.springframework.data.jpa.repository.Query(value = "select nextval('receipt_no_seq')", nativeQuery = true)
    long nextReceiptNo();

    /** Id cac phieu thu da bi huy cua khach (danh dau "DA HUY" tren so no co phan trang). */
    @org.springframework.data.jpa.repository.Query("select l.reversalOf from DebtLedger l where l.customerId = :c and l.reversalOf is not null")
    List<Long> reversedIds(@org.springframework.data.repository.query.Param("c") Long customerId);

    Page<DebtLedger> findByCustomerIdOrderByIdDesc(Long customerId, Pageable page);
}

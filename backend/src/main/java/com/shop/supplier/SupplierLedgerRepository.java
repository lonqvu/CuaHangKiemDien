package com.shop.supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierLedgerRepository extends JpaRepository<SupplierLedger, Long> {
    Page<SupplierLedger> findBySupplierIdOrderByIdDesc(Long supplierId, Pageable page);
}

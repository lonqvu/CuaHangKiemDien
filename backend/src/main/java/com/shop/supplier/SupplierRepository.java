package com.shop.supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    String WHERE = """
            s.active = true and (:onlyDebt = false or s.debt > 0)
              and (:q = '' or lower(s.name) like :q or s.phone like :q or lower(s.address) like :q)
            """;

    @Query(value = "select s from Supplier s where " + WHERE, countQuery = "select count(s) from Supplier s where " + WHERE)
    Page<Supplier> page(@Param("q") String q, @Param("onlyDebt") boolean onlyDebt, Pageable page);

    /** [so NCC, tong no NCC, so NCC dang no] tren toan bo ket qua loc. */
    @Query("select count(s), coalesce(sum(s.debt), 0), sum(case when s.debt > 0 then 1 else 0 end) from Supplier s where " + WHERE)
    List<Object[]> summary(@Param("q") String q, @Param("onlyDebt") boolean onlyDebt);

    boolean existsByActiveTrueAndNameIgnoreCase(String name);
}

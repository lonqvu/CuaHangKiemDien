package com.shop.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByActiveTrueOrderByName();

    /** q: mau LIKE da ha chu thuong ("" = moi khach). onlyDebt: chi khach dang no. */
    String WHERE = """
            c.active = true and (:onlyDebt = false or c.debt > 0)
              and (:q = '' or lower(c.name) like :q or c.phone like :q or lower(c.address) like :q)
            """;

    @Query(value = "select c from Customer c where " + WHERE, countQuery = "select count(c) from Customer c where " + WHERE)
    Page<Customer> page(@Param("q") String q, @Param("onlyDebt") boolean onlyDebt, Pageable page);

    /** [so khach, tong no, so khach dang no] tren toan bo ket qua loc. */
    @Query("select count(c), coalesce(sum(c.debt), 0), sum(case when c.debt > 0 then 1 else 0 end) from Customer c where " + WHERE)
    List<Object[]> summary(@Param("q") String q, @Param("onlyDebt") boolean onlyDebt);

    boolean existsByActiveTrueAndNameIgnoreCase(String name);

    boolean existsByActiveTrueAndNameIgnoreCaseAndIdNot(String name, Long id);
}

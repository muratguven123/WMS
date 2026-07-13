package com.wms.finance.repository;

import com.wms.finance.entity.FinanceCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FinanceCustomerRepository extends JpaRepository<FinanceCustomer, Long> {

    @Query("""
            SELECT c FROM FinanceCustomer c
            LEFT JOIN FETCH c.defaultCurrency
            LEFT JOIN FETCH c.invoicingCurrency
            WHERE c.id = :id
            """)
    Optional<FinanceCustomer> findByIdWithCurrency(Long id);
}

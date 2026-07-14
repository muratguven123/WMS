package com.wms.finance.repository;

import com.wms.finance.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    @Query("""
            SELECT c FROM Contract c
            JOIN FETCH c.currency
            WHERE c.id = :id
            """)
    Optional<Contract> findByIdWithCurrency(Long id);

    @Query("""
            SELECT c FROM Contract c
            JOIN FETCH c.currency
            WHERE c.customer.id = :customerId
              AND c.startDate <= :dateTime
              AND (c.endDate IS NULL OR c.endDate >= :dateTime)
            """)
    List<Contract> findActiveContracts(
            @org.springframework.data.repository.query.Param("customerId") Long customerId,
            @org.springframework.data.repository.query.Param("dateTime") java.time.LocalDateTime dateTime);
}

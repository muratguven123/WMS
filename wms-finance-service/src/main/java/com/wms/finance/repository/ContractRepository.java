package com.wms.finance.repository;

import com.wms.finance.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    @Query("""
            SELECT c FROM Contract c
            JOIN FETCH c.currency
            WHERE c.id = :id
            """)
    Optional<Contract> findByIdWithCurrency(Long id);
}

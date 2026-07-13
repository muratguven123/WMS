package com.wms.finance.repository;

import com.wms.finance.entity.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {
}

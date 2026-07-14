package com.wms.finance.repository;

import com.wms.finance.entity.CustomerExchangeRate;
import com.wms.finance.entity.enums.RateType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface CustomerExchangeRateRepository extends JpaRepository<CustomerExchangeRate, Long> {

    @Query("""
            SELECT cer FROM CustomerExchangeRate cer
            JOIN FETCH cer.customer
            JOIN FETCH cer.sourceCurrency
            JOIN FETCH cer.targetCurrency
            WHERE cer.customer.id = :customerId
              AND cer.sourceCurrency.code = :sourceCode
              AND cer.targetCurrency.code = :targetCode
              AND cer.rateDate = :rateDate
              AND cer.rateType = :rateType
            """)
    Optional<CustomerExchangeRate> findExactRate(
            @Param("customerId") Long customerId,
            @Param("sourceCode") String sourceCode,
            @Param("targetCode") String targetCode,
            @Param("rateDate") LocalDate rateDate,
            @Param("rateType") RateType rateType);
}

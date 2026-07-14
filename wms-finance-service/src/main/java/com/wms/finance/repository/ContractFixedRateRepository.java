package com.wms.finance.repository;

import com.wms.finance.entity.ContractFixedRate;
import com.wms.finance.entity.enums.RateType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractFixedRateRepository extends JpaRepository<ContractFixedRate, Long> {

    @Query("""
            SELECT cfr FROM ContractFixedRate cfr
            JOIN FETCH cfr.contract
            JOIN FETCH cfr.sourceCurrency
            JOIN FETCH cfr.targetCurrency
            WHERE cfr.contract.id = :contractId
              AND cfr.sourceCurrency.code = :sourceCode
              AND cfr.targetCurrency.code = :targetCode
              AND cfr.rateType = :rateType
              AND cfr.active = true
            """)
    List<ContractFixedRate> findActiveRates(
            @Param("contractId") Long contractId,
            @Param("sourceCode") String sourceCode,
            @Param("targetCode") String targetCode,
            @Param("rateType") RateType rateType);

    @Query("""
            SELECT cfr FROM ContractFixedRate cfr
            WHERE cfr.contract.id = :contractId
              AND cfr.sourceCurrency.id = :sourceCurrencyId
              AND cfr.targetCurrency.id = :targetCurrencyId
              AND cfr.rateType = :rateType
              AND cfr.active = true
              AND (:id IS NULL OR cfr.id <> :id)
            """)
    List<ContractFixedRate> findOverlappingRates(
            @Param("contractId") Long contractId,
            @Param("sourceCurrencyId") Long sourceCurrencyId,
            @Param("targetCurrencyId") Long targetCurrencyId,
            @Param("rateType") RateType rateType,
            @Param("id") Long id);
}

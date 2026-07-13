package com.wms.finance.repository;

import com.wms.finance.entity.ExchangeRate;
import com.wms.finance.entity.enums.RateSource;
import com.wms.finance.entity.enums.RateType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    @Query("""
            SELECT er FROM ExchangeRate er
            JOIN FETCH er.sourceCurrency
            JOIN FETCH er.targetCurrency
            WHERE er.sourceCurrency.code = :sourceCode
              AND er.targetCurrency.code = :targetCode
              AND er.rateDate = :rateDate
              AND er.rateType = :rateType
              AND er.rateSource = :rateSource
            """)
    Optional<ExchangeRate> findExactRate(
            @Param("sourceCode") String sourceCode,
            @Param("targetCode") String targetCode,
            @Param("rateDate") LocalDate rateDate,
            @Param("rateType") RateType rateType,
            @Param("rateSource") RateSource rateSource);

    @Query(value = """
            SELECT er.* FROM finance.exchange_rates er
            JOIN finance.currencies sc ON sc.id = er.source_currency_id
            JOIN finance.currencies tc ON tc.id = er.target_currency_id
            WHERE sc.code = :sourceCode
              AND tc.code = :targetCode
              AND er.rate_type = :rateType
              AND er.rate_date <= :rateDate
              AND er.rate_date >= :minDate
            ORDER BY er.rate_date DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ExchangeRate> findNearestPastRate(
            @Param("sourceCode") String sourceCode,
            @Param("targetCode") String targetCode,
            @Param("rateType") String rateType,
            @Param("rateDate") LocalDate rateDate,
            @Param("minDate") LocalDate minDate);

    @Query(value = """
            SELECT er.* FROM finance.exchange_rates er
            JOIN finance.currencies sc ON sc.id = er.source_currency_id
            JOIN finance.currencies tc ON tc.id = er.target_currency_id
            WHERE sc.code = :sourceCode
              AND tc.code = :targetCode
              AND er.rate_type = :rateType
              AND er.rate_source = :rateSource
              AND er.rate_date <= :rateDate
              AND er.rate_date >= :minDate
            ORDER BY er.rate_date DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ExchangeRate> findNearestPastRate(
            @Param("sourceCode") String sourceCode,
            @Param("targetCode") String targetCode,
            @Param("rateType") String rateType,
            @Param("rateSource") String rateSource,
            @Param("rateDate") LocalDate rateDate,
            @Param("minDate") LocalDate minDate);

    /**
     * Belirtilen tarihten önceki en yakın kur kaydı (hafta sonu fallback).
     */
    Optional<ExchangeRate> findTopBySourceCurrency_IdAndTargetCurrency_IdAndRateTypeAndRateDateBeforeOrderByRateDateDesc(
            Long sourceCurrencyId,
            Long targetCurrencyId,
            RateType rateType,
            LocalDate date);

    @Query("""
            SELECT MAX(er.updatedAt) FROM ExchangeRate er
            WHERE er.rateSource = com.wms.finance.entity.enums.RateSource.TCMB
            """)
    Optional<LocalDateTime> findLatestTcmbUpdateAt();
}

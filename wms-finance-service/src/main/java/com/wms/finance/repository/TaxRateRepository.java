package com.wms.finance.repository;

import com.wms.finance.entity.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRateRepository extends JpaRepository<TaxRate, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<TaxRate> {

    @Query("""
            SELECT r FROM TaxRate r
            JOIN FETCH r.taxType
            WHERE r.taxType.id = :taxTypeId
              AND r.countryId = :countryId
              AND r.active = true
              AND r.startDate <= :onDate
              AND (r.endDate IS NULL OR r.endDate >= :onDate)
              AND (r.locationId IS NULL OR (:locationId IS NOT NULL AND r.locationId = :locationId))
              AND (r.customerId IS NULL OR (:customerId IS NOT NULL AND r.customerId = :customerId))
              AND (r.productType IS NULL OR (:productType IS NOT NULL AND r.productType = :productType))
              AND (r.operationType IS NULL OR (:operationType IS NOT NULL AND r.operationType = :operationType))
            """)
    List<TaxRate> findApplicableRates(
            @Param("taxTypeId") Long taxTypeId,
            @Param("countryId") Long countryId,
            @Param("locationId") Long locationId,
            @Param("customerId") Long customerId,
            @Param("productType") String productType,
            @Param("operationType") String operationType,
            @Param("onDate") LocalDate onDate);

    @Query("""
            SELECT r FROM TaxRate r
            WHERE r.taxType.id = :taxTypeId
              AND r.countryId = :countryId
              AND r.locationId IS NOT DISTINCT FROM :locationId
              AND r.customerId IS NOT DISTINCT FROM :customerId
              AND r.productType IS NOT DISTINCT FROM :productType
              AND r.operationType IS NOT DISTINCT FROM :operationType
              AND r.active = true
              AND r.endDate IS NULL
            """)
    Optional<TaxRate> findActiveOpenRate(
            @Param("taxTypeId") Long taxTypeId,
            @Param("countryId") Long countryId,
            @Param("locationId") Long locationId,
            @Param("customerId") Long customerId,
            @Param("productType") String productType,
            @Param("operationType") String operationType);

    @Query("""
            SELECT COUNT(r) > 0 FROM TaxRate r
            WHERE r.taxType.id = :taxTypeId
              AND r.countryId = :countryId
              AND r.locationId IS NOT DISTINCT FROM :locationId
              AND r.customerId IS NOT DISTINCT FROM :customerId
              AND r.productType IS NOT DISTINCT FROM :productType
              AND r.operationType IS NOT DISTINCT FROM :operationType
              AND r.active = true
              AND r.startDate <= :endDate
              AND (r.endDate IS NULL OR r.endDate >= :startDate)
              AND r.id <> :excludeId
            """)
    boolean existsOverlappingRateExcluding(
            @Param("taxTypeId") Long taxTypeId,
            @Param("countryId") Long countryId,
            @Param("locationId") Long locationId,
            @Param("customerId") Long customerId,
            @Param("productType") String productType,
            @Param("operationType") String operationType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") Long excludeId);

    @Modifying
    @Query("""
            UPDATE TaxRate r
            SET r.endDate = :expireDate,
                r.active = false
            WHERE r.taxType.id = :taxTypeId
              AND r.countryId = :countryId
              AND r.active = true
              AND r.endDate IS NULL
            """)
    List<TaxRate> findByCountryIdAndLocationIdIsNullAndActiveTrue(Long countryId);
}

package com.wms.finance.tax.audit.repository;

import com.wms.finance.tax.audit.entity.TaxCalculationLog;
import com.wms.finance.tax.audit.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link TaxCalculationLog} için Spring Data JPA repository.
 *
 * <p>Tüm sorgular yalnızca okuma amaçlıdır; entity {@code @Immutable}
 * olduğundan save() dışında update/delete çağrısı yapılmamalıdır.
 */
@Repository
public interface TaxCalculationLogRepository extends JpaRepository<TaxCalculationLog, Long> {

    // -----------------------------------------------------------------------
    // İşlem Referansı Bazlı Sorgular
    // -----------------------------------------------------------------------

    /**
     * Belirli bir işlem tipine ve referans ID'sine ait tüm vergi kayıtlarını getirir.
     * {@code idx_tax_log_tx_type_ref} indeksini kullanır.
     *
     * @param transactionType     işlem tipi (örn: INVOICE_LINE)
     * @param transactionReferenceId ilgili işlemin Long'si
     */
    List<TaxCalculationLog> findByTransactionTypeAndTransactionReferenceId(
            TransactionType transactionType,
            Long transactionReferenceId
    );

    /**
     * Belirli bir referans ID'sine ait tüm log kayıtlarını tip fark etmeksizin getirir.
     */
    List<TaxCalculationLog> findByTransactionReferenceId(Long transactionReferenceId);

    // -----------------------------------------------------------------------
    // Dönemsel Raporlama Sorguları
    // -----------------------------------------------------------------------

    /**
     * Tarih aralığında vergi kayıtlarını sayfalı getirir.
     * {@code idx_tax_log_calculation_date} indeksini kullanır.
     */
    Page<TaxCalculationLog> findByCalculationDateBetween(
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    );

    /**
     * Belirli bir vergi tipi için tarih aralığında toplam hesaplanan vergi tutarını döner.
     * KDV beyannamesi veya dönem raporu hazırlamak için kullanılır.
     */
    @Query("""
            SELECT COALESCE(SUM(l.calculatedTaxAmount), 0)
            FROM TaxCalculationLog l
            WHERE l.taxType.id = :taxTypeId
              AND l.calculationDate BETWEEN :from AND :to
              AND l.exempt = false
            """)
    BigDecimal sumCalculatedTaxAmountByTaxTypeAndDateRange(
            @Param("taxTypeId") Long taxTypeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // -----------------------------------------------------------------------
    // Muafiyet Sorguları
    // -----------------------------------------------------------------------

    /**
     * Muafiyet kodu ile kayıtları getirir (yasal denetim için).
     * {@code idx_tax_log_is_exempt} indeksini kullanır.
     */
    @Query("""
            SELECT l FROM TaxCalculationLog l
            WHERE l.exempt = true
              AND (:exemptionCode IS NULL OR l.exemptionCode = :exemptionCode)
              AND l.calculationDate BETWEEN :from AND :to
            ORDER BY l.calculationDate DESC
            """)
    Page<TaxCalculationLog> findExemptLogs(
            @Param("exemptionCode") String exemptionCode,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    // -----------------------------------------------------------------------
    // Hesaplama Motoru Denetimi
    // -----------------------------------------------------------------------

    /**
     * Belirli bir hesaplama kaynağından (motor versiyonu) üretilen kayıt sayısını döner.
     * Motor migration'larında regresyon kontrolü için kullanılır.
     */
    long countByCalculationSource(String calculationSource);

    /**
     * İşlem tipi bazında toplam vergi tutarını gruplayarak döner.
     * Dashboard veya raporlama servisi için özet veri sağlar.
     */
    @Query("""
            SELECT l.transactionType, SUM(l.calculatedTaxAmount)
            FROM TaxCalculationLog l
            WHERE l.calculationDate BETWEEN :from AND :to
              AND l.exempt = false
            GROUP BY l.transactionType
            ORDER BY l.transactionType
            """)
    List<Object[]> sumTaxAmountGroupByTransactionType(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}

package com.wms.finance.tax.audit.repository;

import com.wms.finance.tax.audit.entity.TaxType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link TaxType} için Spring Data JPA repository.
 */
@Repository
public interface AuditTaxTypeRepository extends JpaRepository<TaxType, Long> {

    /**
     * Benzersiz kod ile aktif vergi tipini getirir.
     *
     * @param code vergi tipi kodu (örn: "KDV_20")
     */
    Optional<TaxType> findByCodeAndActiveTrue(String code);

    /**
     * Tüm aktif vergi tiplerini döner (UI seçim listesi için).
     */
    List<TaxType> findAllByActiveTrueOrderByCodeAsc();

    /**
     * Koda göre vergi tipinin var olup olmadığını kontrol eder.
     */
    boolean existsByCode(String code);

    /**
     * Aktif vergi tiplerini kod listesiyle toplu getirir.
     */
    @Query("SELECT t FROM TaxType t WHERE t.code IN :codes AND t.active = true")
    List<TaxType> findActiveByCodes(List<String> codes);
}

package com.wms.localization.repository.address;

import com.wms.localization.domain.address.CountryAddressTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CountryAddressTemplateRepository extends JpaRepository<CountryAddressTemplate, Long> {

    /**
     * Ülkeye ait tüm alan şablonlarını sıralı olarak döner (field key eager fetch).
     */
    @Query("""
            SELECT cat FROM CountryAddressTemplate cat
            JOIN FETCH cat.addressTemplateField
            WHERE cat.countryId = :countryId
            ORDER BY cat.sequence ASC
            """)
    List<CountryAddressTemplate> findByCountryIdOrderBySequenceAsc(@Param("countryId") Long countryId);

    /**
     * Ülkeye ait sadece zorunlu alanları döner.
     */
    List<CountryAddressTemplate> findByCountryIdAndMandatoryTrueOrderBySequenceAsc(Long countryId);

    /**
     * Belirli bir alan için tüm ülke şablonlarını döner (alan kaldırma/güncelleme).
     */
    @Query("""
            SELECT cat FROM CountryAddressTemplate cat
            JOIN FETCH cat.addressTemplateField atf
            WHERE atf.fieldKey = :fieldKey
            """)
    List<CountryAddressTemplate> findAllByFieldKey(@Param("fieldKey") String fieldKey);

    /**
     * Ülke + alan kombinasyonu zaten tanımlı mı?
     */
    boolean existsByCountryIdAndAddressTemplateField_FieldKey(Long countryId, String fieldKey);

    boolean existsByCountryIdAndAddressTemplateField_Id(Long countryId, Long fieldId);

    Optional<CountryAddressTemplate> findByIdAndCountryId(Long id, Long countryId);

    void deleteByCountryId(Long countryId);

    @Query("SELECT COALESCE(MAX(cat.sequence), 0) FROM CountryAddressTemplate cat WHERE cat.countryId = :countryId")
    int maxSequenceByCountryId(@Param("countryId") Long countryId);
}

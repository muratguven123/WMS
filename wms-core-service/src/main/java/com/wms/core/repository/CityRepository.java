package com.wms.core.repository;

import com.wms.core.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CityRepository extends JpaRepository<City, Long> {

    /** Cascade dropdown: eyalet yapısı olmayan ülkeler için doğrudan ülkeye bağlı şehirler. */
    List<City> findByCountryIdAndStateProvinceIsNullOrderByNameAsc(Long countryId);

    List<City> findByCountryIdOrderByNameAsc(Long countryId);

    /** Cascade dropdown: eyalet seçilince bağlı şehirler. */
    List<City> findByStateProvinceIdOrderByNameAsc(Long stateProvinceId);

    /** Cross-validation (İş Kuralı 2): district → city → country tutarlılık kontrolü. */
    boolean existsByIdAndCountryId(Long cityId, Long countryId);

    boolean existsByCountryIdAndName(Long countryId, String name);

    // ------------------------------------------------------------------
    // İş İsteri 18 — Admin CRUD tekillik ve sayaç sorguları
    // ------------------------------------------------------------------

    /** Tekillik: eyalete bağlı şehirlerde ad kontrolü. */
    boolean existsByStateProvinceIdAndName(Long stateProvinceId, String name);

    /** Tekillik: doğrudan ülkeye bağlı (eyaletsiz) şehirlerde ad kontrolü. */
    boolean existsByCountryIdAndStateProvinceIsNullAndName(Long countryId, String name);

    /** Eyalet pasifleştirme ön kontrolü: eyalete bağlı aktif şehir var mı? */
    boolean existsByStateProvinceId(Long stateProvinceId);

    /** Admin listesi sayacı: ülkedeki aktif şehir sayısı. */
    long countByCountryId(Long countryId);
}

package com.wms.core.repository;

import com.wms.core.entity.StateProvince;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StateProvinceRepository extends JpaRepository<StateProvince, Long> {

    /** Cascade dropdown: ülkeye bağlı aktif eyaletler. */
    List<StateProvince> findByCountryIdOrderByNameAsc(Long countryId);

    boolean existsByIdAndCountryId(Long id, Long countryId);

    Optional<StateProvince> findByCountryIdAndCode(Long countryId, String code);

    boolean existsByCountryIdAndName(Long countryId, String name);

    // ------------------------------------------------------------------
    // İş İsteri 18 — Admin CRUD sayaç sorgusu
    // ------------------------------------------------------------------

    /** Admin listesi sayacı: ülkedeki aktif eyalet sayısı. */
    long countByCountryId(Long countryId);
}

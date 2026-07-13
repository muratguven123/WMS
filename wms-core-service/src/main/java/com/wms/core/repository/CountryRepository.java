package com.wms.core.repository;

import com.wms.core.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CountryRepository extends JpaRepository<Country, Long> {

    Optional<Country> findByIsoCode(String isoCode);

    boolean existsByIsoCode(String isoCode);

    /** Admin panel: isim içeren ülkeleri ara (case-insensitive). */
    List<Country> findByNameContainingIgnoreCase(String name);

    // ------------------------------------------------------------------
    // İş İsteri 18 — Admin CRUD.
    // Entity üzerindeki @SQLRestriction("is_active = true") pasif kayıtları
    // gizlediği için admin tarafında native sorgular kullanılır.
    // ------------------------------------------------------------------

    /** Admin listesi: pasif ülkeler dahil tüm kayıtlar. */
    @Query(value = "SELECT * FROM countries ORDER BY name", nativeQuery = true)
    List<Country> findAllIncludingInactive();

    /** Pasif kayıtlar dahil id ile arama (reaktivasyon akışı). */
    @Query(value = "SELECT * FROM countries WHERE id = :id", nativeQuery = true)
    Optional<Country> findByIdIncludingInactive(@Param("id") Long id);

    /**
     * Pasif kayıtlar dahil iso_code tekillik kontrolü.
     * DB'deki uk_country_iso_code kısıtı pasif kayıtları da kapsadığından,
     * yalnızca aktifleri gören existsByIsoCode yeterli değildir.
     */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM countries WHERE iso_code = :isoCode)", nativeQuery = true)
    boolean existsByIsoCodeIncludingInactive(@Param("isoCode") String isoCode);

    /** Pasif ülkeyi yeniden aktifleştirir (soft-delete geri alma). */
    @Modifying
    @Query(value = "UPDATE countries SET is_active = true, updated_at = now() WHERE id = :id", nativeQuery = true)
    int reactivate(@Param("id") Long id);
}

package com.wms.localization.repository;

import com.wms.localization.entity.LocationFormatOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link LocationFormatOverride} için Spring Data JPA repository.
 * <p>
 * Depo (Location) bazlı format geçersiz kılma kayıtlarını yönetir.
 * Servis katmanı bu repository'yi kullanarak hiyerarşik format çözümlemesi yapar:
 * depo kaydı varsa onu, yoksa {@link CountryFormatConfigRepository#findByCountryId}
 * ile ülke varsayılanını döner.
 * </p>
 */
@Repository
public interface LocationFormatOverrideRepository extends JpaRepository<LocationFormatOverride, Long> {

    /**
     * Verilen depoya ait format geçersiz kılma kaydını döner.
     * {@link Optional#empty()} dönerse ülke varsayılanına fall-back yapılmalıdır.
     *
     * @param locationId wms-core-service Location entity Long'si
     */
    Optional<LocationFormatOverride> findByLocationId(Long locationId);

    /**
     * Verilen depo için geçersiz kılma kaydı var mı kontrol eder.
     *
     * @param locationId wms-core-service Location entity Long'si
     */
    boolean existsByLocationId(Long locationId);
}

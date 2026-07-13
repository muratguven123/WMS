package com.wms.localization.repository;

import com.wms.localization.entity.CountryFormatConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link CountryFormatConfig} için Spring Data JPA repository.
 * <p>
 * Ülke bazlı format yapılandırmalarını CRUD işlemleri ve
 * countryId'ye göre sorgulama destekler.
 * </p>
 */
@Repository
public interface CountryFormatConfigRepository extends JpaRepository<CountryFormatConfig, Long> {

    /**
     * Verilen ülkeye ait format yapılandırmasını döner.
     * Sonuç boşsa o ülke için henüz konfigürasyon tanımlanmamış demektir.
     *
     * @param countryId wms-core-service Country entity Long'si
     */
    Optional<CountryFormatConfig> findByCountryId(Long countryId);

    /**
     * Verilen ülke için format kaydı var mı kontrol eder.
     * Yeni kayıt eklemeden önce duplicate kısıtını uygulama katmanında
     * erken yakalamak için kullanılır.
     *
     * @param countryId wms-core-service Country entity Long'si
     */
    boolean existsByCountryId(Long countryId);
}

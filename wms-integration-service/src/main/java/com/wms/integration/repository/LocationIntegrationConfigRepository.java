package com.wms.integration.repository;

import com.wms.integration.entity.LocationIntegrationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationIntegrationConfigRepository extends JpaRepository<LocationIntegrationConfig, Long> {

    /**
     * Lokasyon için aktif ve ERP sistemi aktif olan config'i döner.
     * ErpAdapterFactory'nin birincil sorgusudur.
     */
    @Query("""
            SELECT c FROM LocationIntegrationConfig c
            JOIN FETCH c.integrationSystem s
            WHERE c.locationId = :locationId
              AND c.isActive = true
              AND s.isActive = true
            """)
    Optional<LocationIntegrationConfig> findActiveByLocationId(@Param("locationId") Long locationId);

    /**
     * Belirli ERP sistemine bağlı tüm aktif konfigürasyonları listeler.
     * Sistem bazlı toplu işlemler (kur çekme, bakım penceresi vb.) için kullanılır.
     */
    List<LocationIntegrationConfig> findByIntegrationSystem_CodeAndIsActiveTrue(String erpCode);
}

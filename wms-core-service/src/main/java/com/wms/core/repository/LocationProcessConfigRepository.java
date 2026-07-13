package com.wms.core.repository;

import com.wms.core.entity.LocationProcessConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationProcessConfigRepository extends JpaRepository<LocationProcessConfig, Long> {

    /**
     * Lokasyon + süreç tanımı çiftine göre konfigürasyon getirir.
     */
    Optional<LocationProcessConfig> findByLocationIdAndProcessDefinitionId(
            Long locationId, Long processDefinitionId);

    /**
     * Bir lokasyonun aktif tüm konfigürasyonlarını süreç adıyla birlikte getirir.
     */
    @Query("""
            SELECT lpc FROM LocationProcessConfig lpc
            JOIN FETCH lpc.processDefinition pd
            WHERE lpc.locationId = :locationId
              AND lpc.isActive = true
            """)
    List<LocationProcessConfig> findActiveByLocationId(@Param("locationId") Long locationId);
}

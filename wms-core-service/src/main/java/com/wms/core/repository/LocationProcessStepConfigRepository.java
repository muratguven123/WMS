package com.wms.core.repository;

import com.wms.core.entity.LocationProcessStepConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationProcessStepConfigRepository extends JpaRepository<LocationProcessStepConfig, Long> {

    /**
     * Belirli bir lokasyon-süreç konfigürasyonuna ait aktif adımları
     * lokasyon bazlı sıralamaya göre getirir.
     */
    @Query("""
            SELECT sc FROM LocationProcessStepConfig sc
            JOIN FETCH sc.processStepDefinition psd
            WHERE sc.locationProcessConfig.id = :configId
              AND sc.isActive = true
            ORDER BY sc.sequence ASC
            """)
    List<LocationProcessStepConfig> findActiveStepsByConfigId(@Param("configId") Long configId);

    /**
     * Verilen sequence numarasının aynı konfigürasyonda zaten kullanılıp
     * kullanılmadığını kontrol eder (unique constraint doğrulaması için).
     */
    boolean existsByLocationProcessConfigIdAndSequenceAndIdNot(
            Long locationProcessConfigId, int sequence, Long excludeId);

    @Query("""
            SELECT sc FROM LocationProcessStepConfig sc
            JOIN FETCH sc.locationProcessConfig lpc
            JOIN FETCH lpc.processDefinition pd
            WHERE sc.id = :id
            """)
    java.util.Optional<LocationProcessStepConfig> findWithProcessById(@Param("id") Long id);
}

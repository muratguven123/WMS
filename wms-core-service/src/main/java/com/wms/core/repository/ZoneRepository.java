package com.wms.core.repository;

import com.wms.core.entity.Zone;
import com.wms.core.entity.enums.ZoneType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {

    /** Bir depoya ait tüm aktif zone'ları getirir. */
    List<Zone> findByLocationId(Long locationId);

    /** Bir depoya ait belirli tipteki zone'ları getirir. */
    List<Zone> findByLocationIdAndType(Long locationId, ZoneType type);

    /**
     * Belirtilen depoda, verilen code'a sahip zone'u getirir.
     * Code, aynı depo içinde unique olduğu için Optional döner.
     */
    Optional<Zone> findByLocationIdAndCode(Long locationId, String code);

    /**
     * Belirtilen depoda verilen code'un başka bir zone'a ait olup olmadığını kontrol eder.
     * Güncelleme (update) sırasında self-check için excludeId kullanılır.
     */
    @Query("""
        SELECT COUNT(z) > 0
        FROM Zone z
        WHERE z.location.id = :locationId
          AND z.code = :code
          AND z.id <> :excludeId
        """)
    boolean existsByLocationIdAndCodeExcluding(
        @Param("locationId") Long locationId,
        @Param("code") String code,
        @Param("excludeId") Long excludeId
    );
}

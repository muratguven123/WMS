package com.wms.core.repository;

import com.wms.core.entity.StorageLocation;
import com.wms.core.entity.enums.StorageLocationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {

    // -----------------------------------------------------------------------
    // Temel Aramalar
    // -----------------------------------------------------------------------

    /** Address code ile raf gözünü getirir (globally unique). */
    Optional<StorageLocation> findByAddressCode(String addressCode);

    /** Bir zone'a ait tüm raf gözlerini getirir. */
    List<StorageLocation> findByZoneId(Long zoneId);

    /** Bir zone'a ait, belirli statüdeki raf gözlerini getirir. */
    List<StorageLocation> findByZoneIdAndStatus(Long zoneId, StorageLocationStatus status);

    // -----------------------------------------------------------------------
    // Filtreleme ve Arama (Prompt 5.3 — GET /api/locations/search)
    // -----------------------------------------------------------------------

    /**
     * Zone, durum ve koridor bilgisine göre filtrelenmiş, sayfalanmış raf gözü listesi.
     * Null gelen parametreler filtre dışında tutulur.
     */
    @Query("""
        SELECT sl FROM StorageLocation sl
        WHERE (:zoneId   IS NULL OR sl.zone.id = :zoneId)
          AND (:status   IS NULL OR sl.status  = :status)
          AND (:aisle    IS NULL OR sl.aisle   = :aisle)
          AND (:isActive IS NULL OR sl.isActive = :isActive)
        """)
    Page<StorageLocation> searchLocations(
        @Param("zoneId") Long zoneId,
        @Param("status")   StorageLocationStatus status,
        @Param("aisle")    String aisle,
        @Param("isActive") Boolean isActive,
        Pageable pageable
    );

    @Query("""
        SELECT sl FROM StorageLocation sl
        WHERE sl.zone.id IN :zoneIds
          AND (:status   IS NULL OR sl.status  = :status)
          AND (:aisle    IS NULL OR sl.aisle   = :aisle)
          AND (:isActive IS NULL OR sl.isActive = :isActive)
        """)
    Page<StorageLocation> searchLocationsInZones(
        @Param("zoneIds") List<Long> zoneIds,
        @Param("status") StorageLocationStatus status,
        @Param("aisle") String aisle,
        @Param("isActive") Boolean isActive,
        Pageable pageable
    );

    // -----------------------------------------------------------------------
    // Kapasite Sorgular (Prompt 5.2 ve 5.3)
    // -----------------------------------------------------------------------

    /**
     * Doluluk oranı (currentVolume / maxVolume) eşik değerinin üzerinde olan
     * kritik raf gözlerini getirir.
     *
     * Örn: threshold = 0.80 → %80 ve üzeri dolu gözler listelenir.
     * NULLIF kullanımı: maxVolume = 0 durumunda sıfıra bölme hatasını önler.
     */
    @Query("""
        SELECT sl FROM StorageLocation sl
        WHERE sl.maxVolume > 0
          AND (sl.currentVolume / sl.maxVolume) >= :threshold
        ORDER BY (sl.currentVolume / sl.maxVolume) DESC
        """)
    List<StorageLocation> findLocationsAboveVolumeUtilization(
        @Param("threshold") BigDecimal threshold
    );

    @Query("""
        SELECT sl FROM StorageLocation sl
        WHERE sl.zone.id IN :zoneIds
          AND sl.maxVolume > 0
          AND (sl.currentVolume / sl.maxVolume) >= :threshold
        ORDER BY (sl.currentVolume / sl.maxVolume) DESC
        """)
    List<StorageLocation> findLocationsAboveVolumeUtilizationInZones(
        @Param("zoneIds") List<Long> zoneIds,
        @Param("threshold") BigDecimal threshold
    );

    /**
     * Verilen zone'da yeterli hacim ve ağırlık kapasitesi olan,
     * ACTIVE durumdaki raf gözlerini getirir.
     * Directed Putaway (Faz 6) tarafından kullanılır.
     */
    @Query("""
        SELECT sl FROM StorageLocation sl
        WHERE sl.zone.id = :zoneId
          AND sl.status = com.wms.core.entity.enums.StorageLocationStatus.ACTIVE
          AND (sl.maxVolume - sl.currentVolume) >= :requiredVolume
          AND (sl.maxWeight - sl.currentWeight) >= :requiredWeight
        ORDER BY (sl.maxVolume - sl.currentVolume) ASC
        """)
    List<StorageLocation> findAvailableLocationsInZone(
        @Param("zoneId") Long zoneId,
        @Param("requiredVolume") BigDecimal requiredVolume,
        @Param("requiredWeight") BigDecimal requiredWeight
    );
}

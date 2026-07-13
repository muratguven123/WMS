package com.wms.core.service;

import com.wms.core.dto.StorageLocationResponse;
import com.wms.core.dto.StorageLocationStatusUpdateRequest;
import com.wms.core.entity.StorageLocation;
import com.wms.core.entity.enums.StorageLocationStatus;
import com.wms.core.exception.BusinessException;
import com.wms.core.messaging.LocationUpdatedEventFactory;
import com.wms.core.repository.StorageLocationRepository;
import com.wms.core.repository.ZoneRepository;
import com.wms.core.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageLocationService {

    private static final BigDecimal DEFAULT_UTILIZATION_THRESHOLD = new BigDecimal("0.80");

    private final StorageLocationRepository storageLocationRepository;
    private final ZoneRepository zoneRepository;
    private final LocationUpdatedEventFactory locationUpdatedEventFactory;

    /**
     * Depo gözü durumunu günceller (ACTIVE / BLOCKED).
     * FULL durumu yalnızca kapasite servisi tarafından atanır.
     */
    @Transactional
    public StorageLocationResponse updateStatus(Long storageLocationId,
                                                StorageLocationStatusUpdateRequest request) {
        StorageLocation location = findLocationOrThrow(storageLocationId);

        if (request.status() == StorageLocationStatus.FULL) {
            throw new BusinessException("FULL status cannot be set manually", HttpStatus.BAD_REQUEST);
        }

        if (request.status() == StorageLocationStatus.ACTIVE) {
            boolean atCapacity = location.getCurrentVolume().compareTo(location.getMaxVolume()) >= 0
                    || location.getCurrentWeight().compareTo(location.getMaxWeight()) >= 0;
            location.setStatus(atCapacity ? StorageLocationStatus.FULL : StorageLocationStatus.ACTIVE);
        } else {
            location.setStatus(request.status());
        }

        log.info("Storage location status updated: id={}, address={}, status={}",
                location.getId(), location.getAddressCode(), location.getStatus());

        StorageLocation saved = storageLocationRepository.save(location);
        locationUpdatedEventFactory.publish(saved);
        return StorageLocationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<StorageLocationResponse> search(Long zoneId,
                                                StorageLocationStatus status,
                                                String aisle,
                                                Boolean isActive,
                                                Pageable pageable) {
        Long activeLocationId = TenantContextHolder.getLocationId();
        if (zoneId != null) {
            validateZoneBelongsToActiveLocation(zoneId, activeLocationId);
            return storageLocationRepository
                    .searchLocations(zoneId, status, aisle, isActive, pageable)
                    .map(StorageLocationResponse::from);
        }

        var zoneIds = zoneRepository.findByLocationId(activeLocationId).stream()
                .map(z -> z.getId())
                .toList();
        if (zoneIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return storageLocationRepository
                .searchLocationsInZones(zoneIds, status, aisle, isActive, pageable)
                .map(StorageLocationResponse::from);
    }

    /**
     * Hacim doluluk oranı eşik değerinin (%80 varsayılan) üzerindeki kritik gözleri listeler.
     */
    @Transactional(readOnly = true)
    public List<StorageLocationResponse> findCriticalUtilization(BigDecimal thresholdPercent) {
        BigDecimal threshold = thresholdPercent != null
                ? thresholdPercent.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_EVEN)
                : DEFAULT_UTILIZATION_THRESHOLD;

        Long activeLocationId = TenantContextHolder.getLocationId();
        var zoneIds = zoneRepository.findByLocationId(activeLocationId).stream()
                .map(z -> z.getId())
                .toList();
        if (zoneIds.isEmpty()) {
            return List.of();
        }

        return storageLocationRepository.findLocationsAboveVolumeUtilizationInZones(zoneIds, threshold).stream()
                .map(StorageLocationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StorageLocationResponse findById(Long id) {
        return StorageLocationResponse.from(findLocationOrThrow(id));
    }

    private StorageLocation findLocationOrThrow(Long id) {
        StorageLocation location = storageLocationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        "Storage location not found: " + id, HttpStatus.NOT_FOUND));
        Long activeLocationId = TenantContextHolder.getLocationId();
        validateZoneBelongsToActiveLocation(location.getZone().getId(), activeLocationId);
        return location;
    }

    private void validateZoneBelongsToActiveLocation(Long zoneId, Long activeLocationId) {
        zoneRepository.findById(zoneId).ifPresentOrElse(zone -> {
            if (!zone.getLocation().getId().equals(activeLocationId)) {
                throw new BusinessException(
                        "Zone does not belong to active warehouse",
                        HttpStatus.FORBIDDEN);
            }
        }, () -> {
            throw new BusinessException("Zone not found: " + zoneId, HttpStatus.NOT_FOUND);
        });
    }
}

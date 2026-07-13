package com.wms.core.dto;

import com.wms.core.entity.StorageLocation;
import com.wms.core.entity.enums.StorageLocationStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record StorageLocationResponse(
        Long id,
        Long zoneId,
        Long locationId,
        String zoneCode,
        String zoneType,
        String addressCode,
        String aisle,
        String bay,
        String shelf,
        String bin,
        BigDecimal maxVolume,
        BigDecimal maxWeight,
        BigDecimal currentVolume,
        BigDecimal currentWeight,
        BigDecimal volumeUtilizationPercent,
        StorageLocationStatus status,
        boolean active
) {
    public static StorageLocationResponse from(StorageLocation location) {
        BigDecimal utilization = BigDecimal.ZERO;
        if (location.getMaxVolume() != null
                && location.getMaxVolume().compareTo(BigDecimal.ZERO) > 0
                && location.getCurrentVolume() != null) {
            utilization = location.getCurrentVolume()
                    .divide(location.getMaxVolume(), 6, RoundingMode.HALF_EVEN)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_EVEN);
        }

        return new StorageLocationResponse(
                location.getId(),
                location.getZone().getId(),
                location.getZone().getLocation().getId(),
                location.getZone().getCode(),
                location.getZone().getType() != null ? location.getZone().getType().name() : null,
                location.getAddressCode(),
                location.getAisle(),
                location.getBay(),
                location.getShelf(),
                location.getBin(),
                location.getMaxVolume(),
                location.getMaxWeight(),
                location.getCurrentVolume(),
                location.getCurrentWeight(),
                utilization,
                location.getStatus(),
                location.isActive());
    }
}

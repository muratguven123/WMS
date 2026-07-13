package com.wms.inbound.dto;

import java.math.BigDecimal;

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
}

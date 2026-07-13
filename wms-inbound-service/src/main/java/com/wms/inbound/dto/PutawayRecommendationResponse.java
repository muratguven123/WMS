package com.wms.inbound.dto;

import java.math.BigDecimal;

public record PutawayRecommendationResponse(
        String productCode,
        Long storageLocationId,
        String addressCode,
        BigDecimal volumeUtilizationPercent
) {
}

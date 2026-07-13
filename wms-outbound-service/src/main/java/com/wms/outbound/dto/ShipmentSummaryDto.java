package com.wms.outbound.dto;

import com.wms.outbound.entity.enums.ShipmentStatus;


public record ShipmentSummaryDto(
        Long id,
        String shipmentNumber,
        ShipmentStatus status
) {}

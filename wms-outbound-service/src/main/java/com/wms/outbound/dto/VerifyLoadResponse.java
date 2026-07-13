package com.wms.outbound.dto;


public record VerifyLoadResponse(
        Long shipmentId,
        String boxSsccNumber,
        String status,
        boolean allLoaded
) {}

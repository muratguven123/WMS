package com.wms.outbound.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarrierResponseDto {
    private String trackingNumber;
    private String labelBase64;
    private String carrierCode;
    private boolean success;
    private String errorMessage;
}

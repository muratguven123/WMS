package com.wms.outbound.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingAddressDto {
    private String recipientName;
    private String street;
    private String city;
    private String postalCode;
    private String countryCode;
}

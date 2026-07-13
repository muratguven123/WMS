package com.wms.localization.dto.address;

import java.util.Map;

/**
 * Kaydedilmiş adres yanıt DTO'su.
 */
public record AddressResponse(
        Long id,
        Long countryId,
        String city,
        String state,
        String zipCode,
        Map<String, Object> addressDetails,
        String formattedAddress
) {}

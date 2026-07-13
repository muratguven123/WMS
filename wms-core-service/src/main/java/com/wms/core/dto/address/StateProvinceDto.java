package com.wms.core.dto.address;


/**
 * Eyalet / İl listesi için hafif DTO.
 * code nullable — eyalet kodu olmayan bölgeler için null döner.
 */
public record StateProvinceDto(
        Long   id,
        String name,
        String code   // nullable
) {}

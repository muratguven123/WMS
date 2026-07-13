package com.wms.core.dto.address;


/**
 * Mahalle listesi için hafif DTO — cascade dropdown son adımı.
 * zipCode: UI'da Posta Kodu alanını otomatik doldurmak için taşınır;
 * kullanıcı gerekirse override edebilir (İş Kuralı 4).
 */
public record NeighborhoodDto(
        Long   id,
        String name,
        String zipCode   // nullable — posta kodu otomatik doldurma
) {}

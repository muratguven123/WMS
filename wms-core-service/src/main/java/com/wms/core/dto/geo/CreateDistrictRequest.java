package com.wms.core.dto.geo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** İlçe ekleme isteği (İş İsteri 18). */
public record CreateDistrictRequest(

        @NotBlank(message = "name zorunludur")
        @Size(max = 150, message = "name en fazla 150 karakter olabilir")
        String name
) {}

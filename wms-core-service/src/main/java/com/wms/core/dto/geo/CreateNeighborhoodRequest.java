package com.wms.core.dto.geo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mahalle ekleme isteği (İş İsteri 18). */
public record CreateNeighborhoodRequest(

        @NotBlank(message = "name zorunludur")
        @Size(max = 200, message = "name en fazla 200 karakter olabilir")
        String name,

        @Size(max = 20, message = "zipCode en fazla 20 karakter olabilir")
        String zipCode
) {}

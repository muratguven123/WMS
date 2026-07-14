package com.wms.core.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yeni firma oluşturma isteği.
 *
 * <p>{@code taxNumber} benzersizdir (pasif kayıtlar dahil);
 * mükerrer kayıtta servis 409 döner.</p>
 */
public record CreateCompanyRequest(

        @NotNull(message = "organizationId zorunludur")
        Long organizationId,

        @NotBlank(message = "name zorunludur")
        @Size(max = 200, message = "name en fazla 200 karakter olabilir")
        String name,

        @NotBlank(message = "taxNumber zorunludur")
        @Size(min = 5, max = 50, message = "taxNumber 5-50 karakter olmalıdır")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "taxNumber yalnızca harf ve rakam içerebilir")
        String taxNumber,

        @Size(max = 200, message = "taxOffice en fazla 200 karakter olabilir")
        String taxOffice
) {}

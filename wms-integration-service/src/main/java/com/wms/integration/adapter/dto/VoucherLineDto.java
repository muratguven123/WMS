package com.wms.integration.adapter.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/**
 * Muhasebe fişi satırı — borç/alacak kaydı.
 *
 * <p>Çift taraflı kayıt kuralı gereği fiş genelinde borç ve alacak
 * toplamları eşit olmalıdır; bu doğrulama ERP tarafında da yapılır.
 */
@Value
@Builder
@Jacksonized
public class VoucherLineDto {

    /** Muhasebe hesap kodu (tek düzen hesap planı — örn. 120.01.001). */
    @NotBlank
    String accountCode;

    /** Borç tutarı (satır para biriminde; alacak satırında 0/null). */
    BigDecimal debit;

    /** Alacak tutarı (satır para biriminde; borç satırında 0/null). */
    BigDecimal credit;

    /** Satır para birimi (ISO 4217). */
    String currencyCode;

    /** Yerel para birimine çevrim kuru (kur kilitli senaryolarda zorunlu). */
    BigDecimal exchangeRate;
}

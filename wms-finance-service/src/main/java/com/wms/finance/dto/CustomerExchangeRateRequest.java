package com.wms.finance.dto;

import com.wms.finance.entity.enums.RateType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerExchangeRateRequest(
        @NotBlank
        @Size(min = 3, max = 3, message = "Para birimi kodu 3 karakter olmalıdır")
        String sourceCurrency,

        @NotBlank
        @Size(min = 3, max = 3, message = "Para birimi kodu 3 karakter olmalıdır")
        String targetCurrency,

        @NotNull
        LocalDate rateDate,

        @NotNull
        RateType rateType,

        @NotNull
        @DecimalMin(value = "0.000001", message = "Kur değeri sıfırdan büyük olmalıdır")
        BigDecimal rate
) {}

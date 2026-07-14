package com.wms.finance.dto;

import com.wms.finance.entity.enums.RateType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractFixedRateRequest(
        @NotNull Long sourceCurrencyId,
        @NotNull Long targetCurrencyId,
        @NotNull @DecimalMin(value = "0.000001", message = "Kur değeri sıfırdan büyük olmalıdır") BigDecimal rate,
        @NotNull RateType rateType,
        @NotNull LocalDate validFrom,
        LocalDate validTo,
        Boolean active
) {}

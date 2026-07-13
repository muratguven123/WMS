package com.wms.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ExchangeDifferenceRequest(
        @NotNull @DecimalMin(value = "0.0001", message = "Ödenen tutar sıfırdan büyük olmalıdır")
        BigDecimal paidAmountOriginal,

        @NotNull @DecimalMin(value = "0.000001", message = "Ödeme kuru sıfırdan büyük olmalıdır")
        BigDecimal rateAtPayment
) {}

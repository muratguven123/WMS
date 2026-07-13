package com.wms.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CreateContractRequest(
        @NotNull Long customerId,
        @NotNull Long currencyId,
        @NotBlank String contractCode,
        @NotNull LocalDateTime startDate,
        LocalDateTime endDate
) {}

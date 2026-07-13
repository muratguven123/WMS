package com.wms.outbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RequestShippingLabelRequest(
        @NotNull @Valid ShippingAddressDto address
) {}

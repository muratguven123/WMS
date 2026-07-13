package com.wms.core.dto.ui;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TablePreferenceRequest(
        @NotEmpty List<@Valid ColumnPreferenceEntry> columns
) {}

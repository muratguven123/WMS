package com.wms.core.dto.user;

import jakarta.validation.constraints.Size;

public record UserPreferencesRequest(
        @Size(max = 5)
        String preferredLanguage,

        @Size(max = 50)
        String preferredTimezone
) {}

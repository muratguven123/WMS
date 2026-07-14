package com.wms.core.dto.user;

import java.util.List;

public record UserSummaryDto(
        Long id,
        String username,
        String email,
        String keycloakUserId,
        boolean active,
        List<UserAccessDto> accesses,
        String preferredLanguage,
        String preferredTimezone
) {}

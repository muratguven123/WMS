package com.wms.core.dto.user;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateUserRolesRequest(
        @NotEmpty List<String> roles
) {}

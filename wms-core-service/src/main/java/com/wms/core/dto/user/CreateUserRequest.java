package com.wms.core.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUserRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 4, max = 100) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotEmpty List<String> keycloakRoles,
        @NotEmpty List<UserAccessInput> accesses
) {
    public record UserAccessInput(
            @NotBlank Long companyId,
            Long locationId,
            @NotBlank Long roleId
    ) {}
}

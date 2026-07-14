package com.wms.core.controller;

import com.wms.core.dto.user.CreateUserRequest;
import com.wms.core.dto.user.RoleSummaryDto;
import com.wms.core.dto.user.UserSummaryDto;
import com.wms.core.repository.RoleRepository;
import com.wms.core.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WMS_ADMIN')")
public class UserManagementController {

    private final UserManagementService userManagementService;
    private final RoleRepository roleRepository;

    @GetMapping("/roles")
    public ResponseEntity<List<RoleSummaryDto>> listRoles() {
        return ResponseEntity.ok(roleRepository.findAll().stream()
                .map(r -> new RoleSummaryDto(r.getId(), r.getName()))
                .toList());
    }

    @GetMapping("/keycloak-roles")
    public ResponseEntity<List<String>> listKeycloakRoles() {
        return ResponseEntity.ok(List.of(
                "WMS_ADMIN", "WAREHOUSE_MANAGER", "INBOUND_CLERK", "INVENTORY_CLERK",
                "PICKER", "PACKER", "SHIPPING_CLERK", "FINANCE_USER", "FINANCE_MANAGER",
                "INTEGRATION_ADMIN", "LOCALIZATION_ADMIN"));
    }

    @GetMapping
    public ResponseEntity<List<UserSummaryDto>> listUsers() {
        return ResponseEntity.ok(userManagementService.listUsers());
    }

    @PostMapping
    public ResponseEntity<UserSummaryDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementService.createUser(request));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long userId) {
        userManagementService.deactivateUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable Long userId) {
        userManagementService.activateUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/preferences")
    @PreAuthorize("hasRole('WMS_ADMIN') or (isAuthenticated() and #id == @tenantSecurityHelper.currentUserId)")
    public ResponseEntity<UserSummaryDto> updatePreferences(
            @PathVariable Long id,
            @Valid @RequestBody com.wms.core.dto.user.UserPreferencesRequest request) {
        return ResponseEntity.ok(userManagementService.updatePreferences(id, request));
    }

    @PostMapping("/{userId}/access")
    public ResponseEntity<UserSummaryDto> grantAccess(
            @PathVariable Long userId,
            @Valid @RequestBody com.wms.core.dto.user.GrantAccessRequest request) {
        return ResponseEntity.ok(userManagementService.grantAccess(userId, request));
    }

    @DeleteMapping("/{userId}/access/{accessId}")
    public ResponseEntity<UserSummaryDto> revokeAccess(
            @PathVariable Long userId,
            @PathVariable Long accessId) {
        return ResponseEntity.ok(userManagementService.revokeAccess(userId, accessId));
    }

    @PutMapping("/{userId}/roles")
    public ResponseEntity<Void> updateUserRoles(
            @PathVariable Long userId,
            @Valid @RequestBody com.wms.core.dto.user.UpdateUserRolesRequest request) {
        userManagementService.updateUserRoles(userId, request.roles());
        return ResponseEntity.noContent().build();
    }
}

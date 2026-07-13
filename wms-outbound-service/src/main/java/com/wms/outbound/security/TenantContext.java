package com.wms.outbound.security;


/**
 * İstek boyunca taşınan immutable tenant bağlamı.
 *
 * <p><b>Not:</b> Bu serviste {@code userId}, Keycloak User ID'sidir (JWT {@code sub}
 * claim'i) — UserAccess tablosu core-service'e ait olduğundan yerel PK eşleştirmesi
 * yalnızca core-service'te yapılır (JWT-stateless strateji).</p>
 */
public record TenantContext(
        Long userId,
        Long companyId,
        Long locationId
) {

    public TenantContext {
        if (userId == null) throw new IllegalArgumentException("userId cannot be null");
        if (companyId == null) throw new IllegalArgumentException("companyId cannot be null");
        if (locationId == null) throw new IllegalArgumentException("locationId cannot be null");
    }
}

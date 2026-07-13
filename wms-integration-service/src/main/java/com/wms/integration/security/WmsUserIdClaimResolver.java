package com.wms.integration.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Locale;
import java.util.Map;

/**
 * JWT {@code wms_user_id} claim'inden sayısal WMS kullanıcı kimliğini çözümler.
 *
 * <p>V16 UUID→BIGINT migrasyonundan önce claim'de eski kullanıcı UUID'si taşınabiliyordu;
 * demo kullanıcı için bilinen legacy eşleme desteklenir.</p>
 */
public final class WmsUserIdClaimResolver {

    /** V14 demo user UUID → BIGINT PK (V16 sonrası 1L). */
    private static final Map<String, Long> LEGACY_UUID_TO_ID = Map.of(
            "55555555-0000-0000-0000-000000000001", 1L
    );

    private WmsUserIdClaimResolver() {}

    public static Long resolveFromAuthentication(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwtAuth) || !auth.isAuthenticated()) {
            return null;
        }
        return resolveClaim(jwtAuth.getToken().getClaimAsString(TenantContextFilter.CLAIM_WMS_USER_ID));
    }

    public static Long resolveClaim(String claim) {
        if (claim == null || claim.isBlank()) {
            return null;
        }
        String trimmed = claim.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            return LEGACY_UUID_TO_ID.get(trimmed.toLowerCase(Locale.ROOT));
        }
    }
}

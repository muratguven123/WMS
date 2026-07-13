package com.wms.integration.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Keycloak JWT'sindeki rolleri Spring Security {@link GrantedAuthority}'lerine çevirir.
 *
 * <p>Keycloak rolleri iki claim altında taşır: {@code realm_access.roles} (realm rolleri)
 * ve {@code resource_access.<client-id>.roles} (client rolleri). Her rol {@code ROLE_}
 * prefix'i ile UPPER_CASE authority'ye map edilir; böylece
 * {@code @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")} doğrudan çalışır.</p>
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String CLAIM_REALM_ACCESS = "realm_access";
    private static final String CLAIM_RESOURCE_ACCESS = "resource_access";
    private static final String CLAIM_ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    /** Authority'ye dönüştürülmeyecek Keycloak yerleşik rolleri. */
    private static final Set<String> EXCLUDED_ROLES = Set.of(
            "offline_access", "uma_authorization", "default-roles-wms-realm");

    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    /** Client rolleri okunacak Keycloak client-id (örn: "wms-api"). Null ise tüm client'lar taranır. */
    private final String clientId;

    public KeycloakJwtAuthenticationConverter(String clientId) {
        this.clientId = clientId;
    }

    public KeycloakJwtAuthenticationConverter() {
        this(null);
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        Collection<GrantedAuthority> scopes = scopeConverter.convert(jwt);
        if (scopes != null) {
            authorities.addAll(scopes);
        }

        extractRoles(jwt.getClaimAsMap(CLAIM_REALM_ACCESS), authorities);

        Map<String, Object> resourceAccess = jwt.getClaimAsMap(CLAIM_RESOURCE_ACCESS);
        if (resourceAccess != null) {
            if (clientId != null) {
                Object client = resourceAccess.get(clientId);
                if (client instanceof Map<?, ?> clientMap) {
                    extractRoles(castMap(clientMap), authorities);
                }
            } else {
                resourceAccess.values().stream()
                        .filter(Map.class::isInstance)
                        .map(v -> castMap((Map<?, ?>) v))
                        .forEach(m -> extractRoles(m, authorities));
            }
        }

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private void extractRoles(Map<String, Object> accessClaim, Set<GrantedAuthority> authorities) {
        if (accessClaim == null) {
            return;
        }
        Object roles = accessClaim.get(CLAIM_ROLES);
        if (roles instanceof List<?> roleList) {
            roleList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(role -> !EXCLUDED_ROLES.contains(role))
                    .map(role -> ROLE_PREFIX + role.toUpperCase(Locale.ROOT).replace('-', '_'))
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}

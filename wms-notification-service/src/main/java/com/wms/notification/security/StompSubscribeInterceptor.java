package com.wms.notification.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class StompSubscribeInterceptor implements ChannelInterceptor {

    /** BIGINT (V16) veya legacy UUID tenant kimliği. */
    private static final String TENANT_ID = "(\\d+|[0-9a-fA-F-]{36})";

    private static final Pattern COMPANY_LOCATION =
            Pattern.compile("^/topic/company\\." + TENANT_ID + "\\.location\\." + TENANT_ID + "\\..+");
    private static final Pattern COMPANY_ONLY =
            Pattern.compile("^/topic/company\\." + TENANT_ID + "\\..+");
    private static final Pattern USER_QUEUE =
            Pattern.compile("^/queue/user\\.([0-9a-fA-F-]{36})\\..+");

    private static final Set<String> MANAGER_ROLES = Set.of(
            "ROLE_WAREHOUSE_MANAGER", "ROLE_WMS_ADMIN");

    private static final Set<String> INTEGRATION_ROLES = Set.of(
            "ROLE_INTEGRATION_ADMIN", "ROLE_WMS_ADMIN");

    @Value("${wms.security.keycloak.client-id:wms-api}")
    private String keycloakClientId;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Object jwtAttr = accessor.getSessionAttributes() != null
                    ? accessor.getSessionAttributes().get(JwtHandshakeInterceptor.ATTR_JWT)
                    : null;
            if (jwtAttr instanceof Jwt jwt) {
                var auth = new KeycloakJwtAuthenticationConverter(keycloakClientId).convert(jwt);
                accessor.setUser(auth);
            }
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            Principal user = accessor.getUser();
            if (destination == null || user == null) {
                throw new IllegalArgumentException("Subscription denied: missing destination or principal");
            }

            if (!isSubscriptionAllowed(destination, user)) {
                log.warn("Subscription denied for user={} destination={}", user.getName(), destination);
                throw new IllegalArgumentException("Subscription not allowed: " + destination);
            }
        }

        return message;
    }

    boolean isSubscriptionAllowed(String destination, Principal user) {
        if (destination.contains(".approvals")) {
            return hasAnyRole(user, MANAGER_ROLES);
        }

        if (destination.contains(".integrations")) {
            return hasAnyRole(user, INTEGRATION_ROLES);
        }

        Matcher userQueue = USER_QUEUE.matcher(destination);
        if (userQueue.matches()) {
            String requestedUserId = userQueue.group(1);
            return requestedUserId.equals(user.getName());
        }

        Matcher companyLocation = COMPANY_LOCATION.matcher(destination);
        if (companyLocation.matches()) {
            return hasTenantHeaders(companyLocation.group(1), companyLocation.group(2));
        }

        Matcher companyOnly = COMPANY_ONLY.matcher(destination);
        if (companyOnly.matches()) {
            return hasTenantHeaders(companyOnly.group(1), null);
        }

        return false;
    }

    private boolean hasAnyRole(Principal user, Set<String> roles) {
        if (user instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(roles::contains);
        }
        return false;
    }

    private boolean hasTenantHeaders(String companyId, String locationId) {
        return companyId != null && !companyId.isBlank()
                && (locationId == null || !locationId.isBlank());
    }
}

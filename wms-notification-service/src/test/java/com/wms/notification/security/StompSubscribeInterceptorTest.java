package com.wms.notification.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StompSubscribeInterceptorTest {

    private StompSubscribeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompSubscribeInterceptor();
        ReflectionTestUtils.setField(interceptor, "keycloakClientId", "wms-api");
    }

    @Test
    @DisplayName("Numeric company integrations topic — WMS_ADMIN allowed")
    void numericIntegrationsTopic_adminAllowed() {
        var user = jwtUser("aaaaaaaa-0000-0000-0000-000000000001", "ROLE_WMS_ADMIN");
        assertThat(interceptor.isSubscriptionAllowed("/topic/company.1.integrations", user)).isTrue();
    }

    @Test
    @DisplayName("Numeric company integrations topic — INTEGRATION_ADMIN allowed")
    void numericIntegrationsTopic_integrationAdminAllowed() {
        var user = jwtUser("aaaaaaaa-0000-0000-0000-000000000001", "ROLE_INTEGRATION_ADMIN");
        assertThat(interceptor.isSubscriptionAllowed("/topic/company.1.integrations", user)).isTrue();
    }

    @Test
    @DisplayName("Numeric company integrations topic — PICKER denied")
    void numericIntegrationsTopic_pickerDenied() {
        var user = jwtUser("aaaaaaaa-0000-0000-0000-000000000001", "ROLE_PICKER");
        assertThat(interceptor.isSubscriptionAllowed("/topic/company.1.integrations", user)).isFalse();
    }

    @Test
    @DisplayName("Legacy UUID company integrations topic — WMS_ADMIN allowed")
    void legacyUuidIntegrationsTopic_adminAllowed() {
        var user = jwtUser("aaaaaaaa-0000-0000-0000-000000000001", "ROLE_WMS_ADMIN");
        assertThat(interceptor.isSubscriptionAllowed(
                "/topic/company.22222222-0000-0000-0000-000000000001.integrations", user)).isTrue();
    }

    @Test
    @DisplayName("Numeric company.location stock topic — any authenticated user allowed")
    void numericCompanyLocationTopic_allowed() {
        var user = jwtUser("aaaaaaaa-0000-0000-0000-000000000001", "ROLE_PICKER");
        assertThat(interceptor.isSubscriptionAllowed(
                "/topic/company.1.location.2.stock", user)).isTrue();
    }

    @Test
    @DisplayName("User tasks queue — owner allowed")
    void userTasksQueue_ownerAllowed() {
        var user = jwtUser("aaaaaaaa-0000-0000-0000-000000000001", "ROLE_PICKER");
        assertThat(interceptor.isSubscriptionAllowed(
                "/queue/user.aaaaaaaa-0000-0000-0000-000000000001.tasks", user)).isTrue();
    }

    @Test
    @DisplayName("User tasks queue — other user denied")
    void userTasksQueue_otherUserDenied() {
        var user = jwtUser("aaaaaaaa-0000-0000-0000-000000000001", "ROLE_PICKER");
        assertThat(interceptor.isSubscriptionAllowed(
                "/queue/user.bbbbbbbb-0000-0000-0000-000000000002.tasks", user)).isFalse();
    }

    @Test
    @DisplayName("Org companies topic — WMS_ADMIN allowed")
    void orgCompaniesTopic_adminAllowed() {
        var user = jwtUser("aaaaaaaa-0000-0000-0000-000000000001", "ROLE_WMS_ADMIN");
        assertThat(interceptor.isSubscriptionAllowed("/topic/org.companies", user)).isTrue();
    }

    @Test
    @DisplayName("Org companies topic — PICKER denied")
    void orgCompaniesTopic_pickerDenied() {
        var user = jwtUser("aaaaaaaa-0000-0000-0000-000000000001", "ROLE_PICKER");
        assertThat(interceptor.isSubscriptionAllowed("/topic/org.companies", user)).isFalse();
    }

    private static JwtAuthenticationToken jwtUser(String sub, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(sub)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("realm_access", java.util.Map.of("roles", List.of(roles)))
                .build();
        var authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .map(a -> (org.springframework.security.core.GrantedAuthority) a)
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, sub);
    }
}

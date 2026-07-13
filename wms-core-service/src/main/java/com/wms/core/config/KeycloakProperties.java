package com.wms.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "wms.keycloak")
public class KeycloakProperties {

    private String url = "http://localhost:8080";
    private String realm = "wms-realm";
    private String clientId = "wms-api";
    private String clientSecret = "wms-api-dev-secret";
    private String adminUsername = "admin";
    private String adminPassword = "admin";

    public String tokenEndpoint() {
        return url + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    public String adminUsersEndpoint() {
        return url + "/admin/realms/" + realm + "/users";
    }

    public String adminRealmRolesEndpoint(String roleName) {
        return url + "/admin/realms/" + realm + "/roles/" + roleName;
    }

    public String adminUserRoleMappingsEndpoint(String userId) {
        return url + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";
    }
}

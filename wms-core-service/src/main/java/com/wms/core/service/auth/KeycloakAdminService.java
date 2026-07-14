package com.wms.core.service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wms.core.config.KeycloakProperties;
import com.wms.core.event.ConfigChangeEvent;
import com.wms.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    private final KeycloakProperties keycloakProperties;
    private final KeycloakAuthService keycloakAuthService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Keycloak'ta kullanıcı oluşturur ve realm rolleri atar.
     *
     * @return Keycloak kullanıcı Long (JWT sub)
     */
    public String createUser(
            String username,
            String email,
            String password,
            String firstName,
            String lastName,
            Long wmsUserId,
            List<String> realmRoles) {

        String adminToken = keycloakAuthService.getAdminToken();
        HttpHeaders headers = authHeaders(adminToken);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("username", username);
        payload.put("email", email);
        payload.put("enabled", true);
        payload.put("emailVerified", true);
        payload.put("firstName", firstName);
        payload.put("lastName", lastName);

        ObjectNode attrs = objectMapper.createObjectNode();
        ArrayNode wmsIdArr = objectMapper.createArrayNode();
        wmsIdArr.add(wmsUserId.toString());
        attrs.set("wms_user_id", wmsIdArr);
        payload.set("attributes", attrs);

        ArrayNode credentials = objectMapper.createArrayNode();
        ObjectNode cred = objectMapper.createObjectNode();
        cred.put("type", "password");
        cred.put("value", password);
        cred.put("temporary", false);
        credentials.add(cred);
        payload.set("credentials", credentials);

        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(
                    keycloakProperties.adminUsersEndpoint(),
                    new HttpEntity<>(payload.toString(), headers),
                    Void.class);

            URI location = response.getHeaders().getLocation();
            if (location == null) {
                throw new BusinessException("Keycloak kullanıcı oluşturulamadı", HttpStatus.BAD_GATEWAY, "KC_CREATE_FAILED");
            }

            String keycloakUserId = location.getPath().replaceAll(".*/", "");
            assignRealmRoles(keycloakUserId, realmRoles, adminToken);
            log.info("Keycloak kullanıcı oluşturuldu → username={}, kcId={}", username, keycloakUserId);
            return keycloakUserId;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Keycloak kullanıcı oluşturma hatası: {}", ex.getMessage());
            throw new BusinessException("Keycloak kullanıcı oluşturulamadı: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY, "KC_CREATE_FAILED");
        }
    }

    public void disableUser(String keycloakUserId) {
        String adminToken = keycloakAuthService.getAdminToken();
        HttpHeaders headers = authHeaders(adminToken);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("enabled", false);

        restTemplate.exchange(
                keycloakProperties.adminUsersEndpoint() + "/" + keycloakUserId,
                HttpMethod.PUT,
                new HttpEntity<>(payload.toString(), headers),
                Void.class);
    }

    public void enableUser(String keycloakUserId) {
        String adminToken = keycloakAuthService.getAdminToken();
        HttpHeaders headers = authHeaders(adminToken);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("enabled", true);

        restTemplate.exchange(
                keycloakProperties.adminUsersEndpoint() + "/" + keycloakUserId,
                HttpMethod.PUT,
                new HttpEntity<>(payload.toString(), headers),
                Void.class);
    }

    public void updateUserRoles(String keycloakUserId, List<String> newRoles) {
        String adminToken = keycloakAuthService.getAdminToken();
        HttpHeaders headers = authHeaders(adminToken);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    keycloakProperties.adminUserRoleMappingsEndpoint(keycloakUserId),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            JsonNode currentRolesNode = objectMapper.readTree(response.getBody());
            ArrayNode rolesToDelete = objectMapper.createArrayNode();
            if (currentRolesNode.isArray()) {
                for (JsonNode role : currentRolesNode) {
                    String roleName = role.path("name").asText();
                    if (List.of("WMS_ADMIN", "WAREHOUSE_MANAGER", "INBOUND_CLERK", "INVENTORY_CLERK",
                                "PICKER", "PACKER", "SHIPPING_CLERK", "FINANCE_USER", "FINANCE_MANAGER",
                                "INTEGRATION_ADMIN", "LOCALIZATION_ADMIN").contains(roleName)) {
                        ObjectNode roleRef = objectMapper.createObjectNode();
                        roleRef.put("id", role.path("id").asText());
                        roleRef.put("name", roleName);
                        rolesToDelete.add(roleRef);
                    }
                }
            }

            if (!rolesToDelete.isEmpty()) {
                restTemplate.exchange(
                        keycloakProperties.adminUserRoleMappingsEndpoint(keycloakUserId),
                        HttpMethod.DELETE,
                        new HttpEntity<>(rolesToDelete.toString(), headers),
                        Void.class);
            }

            assignRealmRoles(keycloakUserId, newRoles, adminToken);
            log.info("Keycloak roles updated for kcId={} to {}", keycloakUserId, newRoles);
        } catch (Exception ex) {
            log.error("Failed to update Keycloak roles for user {}: {}", keycloakUserId, ex.getMessage());
            throw new BusinessException("Keycloak rol güncellemesi başarısız", HttpStatus.BAD_GATEWAY);
        }
    }

    /**
     * Keycloak kullanıcı attribute'undaki {@code wms_user_id} değerini yerel PK ile eşitler.
     *
     * @return attribute güncellendiyse {@code true} — yeni token alınmalıdır
     */
    public boolean syncWmsUserIdIfNeeded(String keycloakUserId, Long wmsUserId) {
        String expected = Objects.toString(wmsUserId, null);
        if (keycloakUserId == null || keycloakUserId.isBlank() || expected == null) {
            return false;
        }

        try {
            String adminToken = keycloakAuthService.getAdminToken();
            HttpHeaders headers = authHeaders(adminToken);

            ResponseEntity<String> response = restTemplate.exchange(
                    keycloakProperties.adminUsersEndpoint() + "/" + keycloakUserId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            JsonNode user = objectMapper.readTree(response.getBody());
            if (!(user instanceof ObjectNode payload)) {
                log.warn("Unexpected Keycloak user JSON for kcId={}", keycloakUserId);
                return false;
            }
            JsonNode attrs = user.path("attributes").path("wms_user_id");
            String current = null;
            if (attrs.isArray() && !attrs.isEmpty()) {
                current = attrs.get(0).asText();
            }

            if (expected.equals(current)) {
                return false;
            }

            ArrayNode wmsIdArr = objectMapper.createArrayNode();
            wmsIdArr.add(expected);
            ObjectNode attributeNode = objectMapper.createObjectNode();
            attributeNode.set("wms_user_id", wmsIdArr);
            payload.set("attributes", attributeNode);

            restTemplate.exchange(
                    keycloakProperties.adminUsersEndpoint() + "/" + keycloakUserId,
                    HttpMethod.PUT,
                    new HttpEntity<>(payload.toString(), headers),
                    Void.class);

            ResponseEntity<String> verify = restTemplate.exchange(
                    keycloakProperties.adminUsersEndpoint() + "/" + keycloakUserId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            JsonNode verifiedAttrs = objectMapper.readTree(verify.getBody())
                    .path("attributes").path("wms_user_id");
            String verified = verifiedAttrs.isArray() && !verifiedAttrs.isEmpty()
                    ? verifiedAttrs.get(0).asText()
                    : null;
            if (!expected.equals(verified)) {
                log.warn("Keycloak wms_user_id verify failed — kcId={}, expected={}, actual={}",
                        keycloakUserId, expected, verified);
                return false;
            }

            log.info("Keycloak wms_user_id synced → kcId={}, wmsUserId={}", keycloakUserId, expected);
            eventPublisher.publishEvent(new ConfigChangeEvent(
                    this,
                    "KeycloakUser",
                    wmsUserId,
                    "SYNC",
                    List.of(new ConfigChangeEvent.FieldChange("wms_user_id", current, expected)),
                    null
            ));
            return true;
        } catch (Exception ex) {
            log.warn("Keycloak wms_user_id sync failed for kcId={}: {}", keycloakUserId, ex.getMessage());
            return false;
        }
    }

    /** Attribute güncellemesi sonrası eski claim'li refresh oturumlarını kapatır. */
    public void revokeUserSessions(String keycloakUserId) {
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            return;
        }
        try {
            String adminToken = keycloakAuthService.getAdminToken();
            HttpHeaders headers = authHeaders(adminToken);
            restTemplate.postForEntity(
                    keycloakProperties.adminUsersEndpoint() + "/" + keycloakUserId + "/logout",
                    new HttpEntity<>(headers),
                    Void.class);
            log.info("Keycloak sessions revoked for kcId={}", keycloakUserId);
        } catch (Exception ex) {
            log.warn("Keycloak session revoke failed for kcId={}: {}", keycloakUserId, ex.getMessage());
        }
    }

    private void assignRealmRoles(String keycloakUserId, List<String> roleNames, String adminToken) {
        HttpHeaders headers = authHeaders(adminToken);
        ArrayNode roles = objectMapper.createArrayNode();

        for (String roleName : roleNames) {
            try {
                ResponseEntity<String> roleResp = restTemplate.exchange(
                        keycloakProperties.adminRealmRolesEndpoint(roleName),
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class);

                JsonNode role = objectMapper.readTree(roleResp.getBody());
                ObjectNode roleRef = objectMapper.createObjectNode();
                roleRef.put("id", role.path("id").asText());
                roleRef.put("name", role.path("name").asText());
                roles.add(roleRef);
            } catch (Exception ex) {
                log.warn("Keycloak rolü bulunamadı: {} — {}", roleName, ex.getMessage());
            }
        }

        if (!roles.isEmpty()) {
            restTemplate.postForEntity(
                    keycloakProperties.adminUserRoleMappingsEndpoint(keycloakUserId),
                    new HttpEntity<>(roles.toString(), headers),
                    Void.class);
        }
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

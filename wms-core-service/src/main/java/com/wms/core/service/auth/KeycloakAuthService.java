package com.wms.core.service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.core.config.KeycloakProperties;
import com.wms.core.dto.auth.TokenResponse;
import com.wms.core.entity.User;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class KeycloakAuthService {

    private final KeycloakProperties keycloakProperties;
    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public KeycloakAuthService(KeycloakProperties keycloakProperties,
                               UserRepository userRepository,
                               @Lazy KeycloakAdminService keycloakAdminService,
                               ObjectMapper objectMapper) {
        this.keycloakProperties = keycloakProperties;
        this.userRepository = userRepository;
        this.keycloakAdminService = keycloakAdminService;
        this.objectMapper = objectMapper;
    }

    public TokenResponse login(String username, String password) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", keycloakProperties.getClientId());
        body.add("client_secret", keycloakProperties.getClientSecret());
        body.add("username", username);
        body.add("password", password);

        try {
            return issueTokenWithSyncedClaim(body);
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new BusinessException("Kullanıcı adı veya şifre hatalı", HttpStatus.UNAUTHORIZED, "AUTH_INVALID");
        } catch (HttpClientErrorException ex) {
            log.warn("Keycloak login hatası: {}", ex.getResponseBodyAsString());
            throw new BusinessException("Giriş başarısız", HttpStatus.UNAUTHORIZED, "AUTH_FAILED");
        }
    }

    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", keycloakProperties.getClientId());
        body.add("client_secret", keycloakProperties.getClientSecret());
        body.add("refresh_token", refreshToken);

        try {
            return issueTokenWithSyncedClaim(body);
        } catch (HttpClientErrorException ex) {
            throw new BusinessException("Oturum yenilenemedi", HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_FAILED");
        }
    }

    /**
     * Token alır; JWT'deki {@code wms_user_id} claim'i yerel kullanıcı PK'si ile uyuşmuyorsa
     * Keycloak attribute'unu günceller ve yeni token döner.
     *
     * <p>Refresh grant ile alınan token'larda Keycloak attribute güncellemesi access token'a
     * yansımayabilir; bu durumda oturumlar sonlandırılır ve istemci yeniden giriş yapmalıdır.</p>
     */
    private TokenResponse issueTokenWithSyncedClaim(MultiValueMap<String, String> body) {
        TokenResponse token = requestToken(body);
        String keycloakSub = parseJwtClaim(token.accessToken(), "sub");
        if (keycloakSub == null) {
            return token;
        }

        Optional<User> localUser = resolveLocalUser(body, keycloakSub);
        if (localUser.isEmpty()) {
            return token;
        }

        String expected = localUser.get().getId().toString();
        if (expected.equals(parseJwtClaim(token.accessToken(), "wms_user_id"))) {
            return token;
        }

        if (!keycloakAdminService.syncWmsUserIdIfNeeded(keycloakSub, localUser.get().getId())) {
            log.warn("wms_user_id claim mismatch and Keycloak sync skipped — sub={}, expected={}",
                    keycloakSub, expected);
            return token;
        }

        keycloakAdminService.revokeUserSessions(keycloakSub);

        if ("refresh_token".equals(body.getFirst("grant_type"))) {
            throw new BusinessException(
                    "Oturum bilgileri güncellendi; lütfen tekrar giriş yapın",
                    HttpStatus.UNAUTHORIZED,
                    "AUTH_RELOGIN_REQUIRED");
        }

        TokenResponse reissued = requestToken(body);
        if (expected.equals(parseJwtClaim(reissued.accessToken(), "wms_user_id"))) {
            return reissued;
        }

        log.warn("wms_user_id claim still invalid after login re-issue — sub={}, expected={}",
                keycloakSub, expected);
        return reissued;
    }

    private Optional<User> resolveLocalUser(MultiValueMap<String, String> body, String keycloakSub) {
        Optional<User> localUser = userRepository.findByKeycloakUserId(keycloakSub);
        if (localUser.isPresent()) {
            return localUser;
        }

        String username = body.getFirst("username");
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        Optional<User> userByUsername = userRepository.findByUsername(username);
        if (userByUsername.isEmpty()) {
            return Optional.empty();
        }

        User user = userByUsername.get();
        user.setKeycloakUserId(keycloakSub);
        userRepository.save(user);
        log.info("Automatically mapped user username={} to keycloakSub={}", username, keycloakSub);
        return Optional.of(user);
    }

    private String parseJwtClaim(String accessToken, String claimName) {
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode node = objectMapper.readTree(payload);
            JsonNode claim = node.get(claimName);
            if (claim == null || claim.isNull()) {
                return null;
            }
            return claim.asText();
        } catch (Exception ex) {
            log.debug("JWT claim parse failed ({}): {}", claimName, ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private TokenResponse requestToken(MultiValueMap<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                keycloakProperties.tokenEndpoint(),
                new HttpEntity<>(body, headers),
                Map.class);

        Map<String, Object> data = response.getBody();
        if (data == null) {
            throw new BusinessException("Keycloak yanıtı boş", HttpStatus.BAD_GATEWAY, "AUTH_EMPTY");
        }

        return new TokenResponse(
                (String) data.get("access_token"),
                (String) data.get("refresh_token"),
                (String) data.get("id_token"),
                ((Number) data.getOrDefault("expires_in", 900)).longValue(),
                (String) data.getOrDefault("token_type", "Bearer"));
    }

    /** Keycloak Admin API token (admin-cli + master realm). */
    public String getAdminToken() {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", "admin-cli");
        body.add("username", keycloakProperties.getAdminUsername());
        body.add("password", keycloakProperties.getAdminPassword());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    keycloakProperties.getUrl() + "/realms/master/protocol/openid-connect/token",
                    new HttpEntity<>(body, headers),
                    Map.class);
            Map<String, Object> data = response.getBody();
            if (data == null || data.get("access_token") == null) {
                throw new BusinessException("Keycloak admin token alınamadı", HttpStatus.BAD_GATEWAY, "KC_ADMIN_TOKEN");
            }
            return (String) data.get("access_token");
        } catch (HttpClientErrorException ex) {
            log.error("Keycloak admin token hatası: {}", ex.getResponseBodyAsString());
            throw new BusinessException("Keycloak admin erişimi başarısız", HttpStatus.BAD_GATEWAY, "KC_ADMIN_TOKEN");
        }
    }
}

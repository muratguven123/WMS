package com.wms.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.core.entity.User;
import com.wms.core.exception.ErrorResponse;
import com.wms.core.repository.UserAccessRepository;
import com.wms.core.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Keycloak JWT doğrulaması SONRASI çalışan tenant yetkilendirme filtresi.
 *
 * <p>Akış:</p>
 * <ol>
 *   <li>Spring Security OAuth2 Resource Server, Bearer token'ın imzasını ve
 *       issuer/expiry değerlerini zaten doğrulamıştır ({@link JwtAuthenticationToken}).</li>
 *   <li>Bu filter JWT'nin {@code sub} claim'ini (Keycloak User ID) okur ve
 *       {@code users.keycloak_user_id} kolonu üzerinden yerel kullanıcıyı bulur.</li>
 *   <li>X-Active-Company-ID / X-Active-Location-ID header'ları okunur ve
 *       kullanıcının bu çifte erişimi {@code UserAccess} tablosundan doğrulanır.</li>
 *   <li>Doğrulanan bilgiler {@link TenantContextHolder}'a (ThreadLocal) yazılır,
 *       istek sonunda temizlenir.</li>
 * </ol>
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    public static final String HEADER_COMPANY_ID  = "X-Active-Company-ID";
    public static final String HEADER_LOCATION_ID = "X-Active-Location-ID";

    /**
     * Tenant context doğrulamasından muaf tutulan path pattern'leri.
     * Auth endpoint'leri, health check, Swagger gibi public endpoint'ler.
     */
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/auth",
            "/api/address",
            "/api/org",
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs"
    );

    private final UserRepository userRepository;
    private final UserAccessRepository userAccessRepository;
    private final ObjectMapper objectMapper;

    public TenantContextFilter(UserRepository userRepository,
                               UserAccessRepository userAccessRepository,
                               ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.userAccessRepository = userAccessRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 1. OAuth2 Resource Server'ın doğruladığı JWT'den Keycloak User ID (sub) al
            String keycloakUserId = extractKeycloakUserId();
            if (keycloakUserId == null) {
                writeError(response, request, HttpStatus.UNAUTHORIZED,
                        "Authentication required. No valid JWT token found.");
                return;
            }

            // 2. Keycloak sub → yerel kullanıcı eşleştirmesi
            Optional<User> localUser = userRepository.findByKeycloakUserId(keycloakUserId);
            if (localUser.isEmpty()) {
                log.warn("No local user mapped to Keycloak sub={}", keycloakUserId);
                writeError(response, request, HttpStatus.FORBIDDEN,
                        "Authenticated identity is not provisioned in WMS. Contact your administrator.");
                return;
            }
            Long userId = localUser.get().getId();

            // 3. Header'lardan company ve location ID'lerini oku
            String companyHeader  = request.getHeader(HEADER_COMPANY_ID);
            String locationHeader = request.getHeader(HEADER_LOCATION_ID);

            if (companyHeader == null || companyHeader.isBlank()) {
                writeError(response, request, HttpStatus.BAD_REQUEST,
                        "Missing required header: " + HEADER_COMPANY_ID);
                return;
            }
            if (locationHeader == null || locationHeader.isBlank()) {
                writeError(response, request, HttpStatus.BAD_REQUEST,
                        "Missing required header: " + HEADER_LOCATION_ID);
                return;
            }

            Long companyId;
            Long locationId;
            try {
                companyId  = Long.parseLong(companyHeader.trim());
                locationId = Long.parseLong(locationHeader.trim());
            } catch (IllegalArgumentException e) {
                writeError(response, request, HttpStatus.BAD_REQUEST,
                        "Invalid numeric ID format in tenant headers.");
                return;
            }

            // 4. UserAccess tablosundan yetki kontrolü
            boolean hasAccess = userAccessRepository.hasAccess(userId, companyId, locationId);
            if (!hasAccess) {
                log.warn("Access denied — userId={}, keycloakSub={}, companyId={}, locationId={}",
                        userId, keycloakUserId, companyId, locationId);
                writeError(response, request, HttpStatus.FORBIDDEN,
                        "You do not have access to the specified company/location.");
                return;
            }

            // 5. Context'i set et
            TenantContext context = new TenantContext(userId, companyId, locationId);
            TenantContextHolder.setContext(context);

            log.debug("TenantContext set — userId={}, companyId={}, locationId={}",
                    userId, companyId, locationId);

            // 6. Filter chain'e devam et
            filterChain.doFilter(request, response);

        } finally {
            // 7. ThreadLocal sızıntısını önle — her durumda temizle
            TenantContextHolder.clear();
        }
    }

    /**
     * Spring SecurityContext'ten Keycloak User ID'sini ({@code sub} claim) çıkarır.
     * OAuth2 Resource Server başarılı doğrulama sonrası {@link JwtAuthenticationToken} koyar;
     * token yoksa veya doğrulanamadıysa null döner.
     */
    private String extractKeycloakUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            // KeycloakJwtAuthenticationConverter principal adını `sub` olarak set eder
            return jwtAuth.getToken().getSubject();
        }

        return null;
    }

    /**
     * Filter seviyesinde JSON hata yanıtı yazar.
     * Controller'a ulaşmadan response'u sonlandırır.
     */
    private void writeError(HttpServletResponse response,
                            HttpServletRequest request,
                            HttpStatus status,
                            String message) throws IOException {

        ErrorResponse error = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}

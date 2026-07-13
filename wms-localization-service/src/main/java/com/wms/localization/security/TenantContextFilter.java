package com.wms.localization.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.localization.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Aktif depo/şirket bağlamını kurar — JWT-stateless strateji.
 *
 * <p>Kullanıcı kimliği artık X-User-ID header'ından DEĞİL, OAuth2 Resource Server'ın
 * doğruladığı Keycloak JWT'sinden alınır (varsa {@code wms_user_id} custom claim'i,
 * yoksa {@code sub}). Company/location bilgisi header'lardan okunur; üyelik doğrulaması
 * UserAccess tablosunun sahibi olan core-service'in sorumluluğundadır.</p>
 */
@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String HEADER_COMPANY_ID  = "X-Active-Company-ID";
    public static final String HEADER_LOCATION_ID = "X-Active-Location-ID";

    /** Keycloak token mapper ile eklenebilen, yerel kullanıcı PK'sini taşıyan custom claim. */
    public static final String CLAIM_WMS_USER_ID = "wms_user_id";

    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs"
    );

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (EXCLUDED_PATHS.stream().anyMatch(path::startsWith)) {
            return true;
        }
        return skipsTenantContext(path, request.getMethod());
    }

    /** Adres CRUD — tenant bağlamı kullanılmaz (global adres kaydı). */
    private static boolean isAddressApi(String path) {
        return path.startsWith("/api/addresses");
    }

    /**
     * OAuth2 JWT doğrulaması yeterli; {@code wms_user_id} ve tenant header gerekmez.
     * i18n bootstrap GET'leri ve sistem geneli localization admin yazma uçları.
     */
    static boolean skipsTenantContext(String path, String method) {
        if (isAuthOnlyGet(path, method)) {
            return true;
        }
        if (isLocalizationAdminWrite(path, method)) {
            return true;
        }
        return isAddressApi(path);
    }

    /** Dil/çeviri yönetimi — tenant bağlamı kullanılmaz, {@code @PreAuthorize} yeterli. */
    private static boolean isLocalizationAdminWrite(String path, String method) {
        String upper = method.toUpperCase();
        if ("POST".equals(upper) && "/api/v1/languages".equals(path)) {
            return true;
        }
        if ("POST".equals(upper) && path.matches("/api/v1/languages/[^/]+/auto-translate")) {
            return true;
        }
        if ("DELETE".equals(upper) && path.matches("/api/v1/languages/[^/]+")) {
            return true;
        }
        return false;
    }

    /**
     * i18n bootstrap GET uçları.
     */
    static boolean isAuthOnlyGet(String path, String method) {
        if (!"GET".equalsIgnoreCase(method)) {
            return false;
        }
        if ("/api/v1/languages".equals(path)) {
            return true;
        }
        if (path.startsWith("/api/v1/translations") && !path.contains("/export")) {
            return true;
        }
        if ("/api/v1/formats/active".equals(path) || "/api/formats/active".equals(path)) {
            return true;
        }
        return path.matches("/api/v1/languages/[^/]+/translation-status");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            Long userId = extractUserId();
            if (userId == null) {
                String message = isJwtAuthenticated()
                        ? "Invalid or missing wms_user_id claim. Please log out and log in again."
                        : "Authentication required. No valid JWT token found.";
                writeError(response, HttpStatus.UNAUTHORIZED, message);
                return;
            }

            Long locationId = parseLongHeader(request, HEADER_LOCATION_ID);
            if (locationId == null) {
                writeError(response, HttpStatus.BAD_REQUEST,
                        "Missing or invalid header: " + HEADER_LOCATION_ID);
                return;
            }

            TenantContext context = new TenantContext(
                    locationId,
                    parseLongHeader(request, HEADER_COMPANY_ID),
                    userId
            );
            TenantContextHolder.setContext(context);
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * Doğrulanmış JWT'den yerel WMS kullanıcı kimliğini çıkarır.
     * Yalnızca sayısal {@code wms_user_id} custom claim'i kabul edilir;
     * Keycloak {@code sub} (UUID) fallback olarak kullanılmaz.
     */
    static Long extractWmsUserId(Authentication auth) {
        return WmsUserIdClaimResolver.resolveFromAuthentication(auth);
    }

    private Long extractUserId() {
        return extractWmsUserId(SecurityContextHolder.getContext().getAuthentication());
    }

    private static boolean isJwtAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwtAuth && jwtAuth.isAuthenticated();
    }

    private Long parseLongHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                new ErrorResponse(status.value(), status.getReasonPhrase(), message, null));
    }
}

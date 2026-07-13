package com.wms.inbound.security;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    public static final String HEADER_COMPANY_ID = "X-Active-Company-ID";
    public static final String HEADER_LOCATION_ID = "X-Active-Location-ID";
    public static final String CLAIM_WMS_USER_ID = "wms_user_id";

    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs"
    );

    private final ObjectMapper objectMapper;

    public TenantContextFilter(ObjectMapper objectMapper) {
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
            Long userId = extractUserId();
            if (userId == null) {
                String message = isJwtAuthenticated()
                        ? "Invalid or missing wms_user_id claim. Please log out and log in again."
                        : "Authentication required. No valid JWT token found.";
                writeError(response, request, HttpStatus.UNAUTHORIZED, message);
                return;
            }

            Long companyId = parseLongHeader(request, HEADER_COMPANY_ID);
            Long locationId = parseLongHeader(request, HEADER_LOCATION_ID);

            if (companyId == null) {
                writeError(response, request, HttpStatus.BAD_REQUEST,
                        "Missing or invalid header: " + HEADER_COMPANY_ID);
                return;
            }
            if (locationId == null) {
                writeError(response, request, HttpStatus.BAD_REQUEST,
                        "Missing or invalid header: " + HEADER_LOCATION_ID);
                return;
            }

            TenantContextHolder.setContext(new TenantContext(userId, companyId, locationId));
            log.debug("TenantContext set — userId={}, companyId={}, locationId={}",
                    userId, companyId, locationId);

            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

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

    private void writeError(HttpServletResponse response,
                            HttpServletRequest request,
                            HttpStatus status,
                            String message) throws IOException {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

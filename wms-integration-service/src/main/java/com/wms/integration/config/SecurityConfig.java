package com.wms.integration.config;

import com.wms.integration.security.KeycloakJwtAuthenticationConverter;
import com.wms.integration.security.TenantContextFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security yapılandırması — Keycloak OAuth2 Resource Server (JWT).
 *
 * <p>Filter sıralaması:</p>
 * <ol>
 *   <li>{@link BearerTokenAuthenticationFilter} — Keycloak JWT imza/issuer/expiry doğrulaması</li>
 *   <li>{@link TenantContextFilter} — JWT-stateless tenant context kurulumu</li>
 *   <li>Controller — {@code @PreAuthorize} rol kontrolleri (method security)</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final TenantContextFilter tenantContextFilter;

    /** Client rollerinin okunacağı Keycloak client-id. */
    @Value("${wms.security.keycloak.client-id:wms-api}")
    private String keycloakClientId;

    /** UI origin'leri — virgülle ayrılmış liste (env: WMS_CORS_ALLOWED_ORIGINS). */
    @Value("${wms.security.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
    private String allowedOriginPatterns;

    public SecurityConfig(TenantContextFilter tenantContextFilter) {
        this.tenantContextFilter = tenantContextFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CORS — UI origin'lerinden gelen isteklere izin ver (bean: corsConfigurationSource).
            // CorsFilter auth filtrelerinden ONCE calisir; preflight (OPTIONS) istekleri
            // Authorization header tasimadigi icin 401'e dusmeden yanitlanir.
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(
                        new KeycloakJwtAuthenticationConverter(keycloakClientId)))
            )
            .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS yapılandırması — UI uygulamasının origin'lerinden gelen isteklere izin verir.
     *
     * <p>Preflight (OPTIONS) isteklerinde {@code Authorization}, {@code X-Active-Company-ID}
     * ve {@code X-Active-Location-ID} header'ları kabul edilir. Bearer token header ile
     * taşındığından cookie tabanlı credential gerekmez ({@code allowCredentials=false}).</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(parseOriginPatterns());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Active-Company-ID",
                "X-Active-Location-ID",
                "X-Requested-With"
        ));
        // UI'nin response'ta okumasi gereken header'lar (orn. dosya indirme adi)
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L); // Preflight cache — 1 saat
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private List<String> parseOriginPatterns() {
        return Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}

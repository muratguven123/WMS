package com.wms.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI yapılandırması (springdoc-openapi).
 *
 * <ul>
 *   <li><b>Güvenlik şeması:</b> Bearer JWT — Swagger UI "Authorize" butonuyla
 *       Keycloak access_token girilir, tüm isteklere otomatik eklenir.</li>
 *   <li><b>Global tenant header'ları:</b> {@code X-Active-Company-ID} ve
 *       {@code X-Active-Location-ID} (Long, zorunlu) tüm operation'lara
 *       otomatik parametre olarak eklenir — her endpoint'te doldurulabilir.</li>
 *   <li><b>Gruplama:</b> {@code /api/**} altındaki endpoint'ler "wms-api"
 *       grubunda listelenir (versiyonlu {@code /api/v1/**} path'leri dahil).</li>
 * </ul>
 *
 * <p>Swagger UI: {@code /swagger-ui.html} — SecurityConfig'te permitAll tanımlıdır.</p>
 */
@Configuration
public class OpenApiConfig {

    /** Swagger UI "Authorize" butonunda görünen güvenlik şeması adı. */
    private static final String BEARER_SCHEME = "bearerAuth";

    private static final String COMPANY_HEADER = "X-Active-Company-ID";
    private static final String LOCATION_HEADER = "X-Active-Location-ID";
    private static final String UUID_EXAMPLE = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

    @Bean
    public OpenAPI wmsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WMS Inventory Service API")
                        .description("Multi-tenant WMS — Inventory mikroservisi. "
                                + "Tüm istekler Keycloak JWT (Bearer) ve aktif firma/lokasyon "
                                + "header'ları gerektirir.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Keycloak access_token — 'Bearer ' öneki otomatik "
                                        + "eklenir, sadece token değerini yapıştırın.")))
                // Global güvenlik gereksinimi — tüm operation'larda kilit ikonu aktif
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /** {@code /api/**} altındaki tüm endpoint'leri "wms-api" grubunda listeler. */
    @Bean
    public GroupedOpenApi wmsApiGroup() {
        return GroupedOpenApi.builder()
                .group("wms-api")
                .pathsToMatch("/api/**")
                .build();
    }

    /**
     * Global tenant header parametreleri — tüm operation'lara otomatik eklenir,
     * Swagger UI'da her endpoint için doldurulabilir alan olarak görünür.
     *
     * <p>{@link GlobalOpenApiCustomizer} kullanıldığı için tanımlı TÜM
     * {@link GroupedOpenApi} gruplarına uygulanır (plain OpenApiCustomizer
     * yalnızca default doc'a uygulanırdı).</p>
     */
    @Bean
    public GlobalOpenApiCustomizer tenantHeadersCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation -> {
                        addHeaderIfAbsent(operation, COMPANY_HEADER,
                                "Aktif firma ID'si (Long) — zorunlu tenant header'ı");
                        addHeaderIfAbsent(operation, LOCATION_HEADER,
                                "Aktif lokasyon ID'si (Long) — zorunlu tenant header'ı");
                    }));
        };
    }

    /** Controller'da {@code @RequestHeader} ile zaten dokümante edilmişse mükerrer eklemez. */
    private static void addHeaderIfAbsent(Operation operation, String name, String description) {
        boolean exists = operation.getParameters() != null && operation.getParameters().stream()
                .anyMatch(p -> name.equalsIgnoreCase(p.getName()));
        if (exists) {
            return;
        }
        Parameter header = new HeaderParameter()
                .name(name)
                .description(description)
                .required(true)
                .schema(new UUIDSchema())
                .example(UUID_EXAMPLE);
        operation.addParametersItem(header);
    }
}

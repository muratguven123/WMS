package com.wms.core.integration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * {@link LocalizationUsageClient}'ın RestTemplate tabanlı implementasyonu.
 *
 * <h3>Tasarım kararları</h3>
 * <ul>
 *   <li><b>Best-effort:</b> Her türlü hata (timeout, 4xx/5xx, parse) yutulur,
 *       WARN loglanır ve {@code Optional.empty()} döner — ülke pasifleştirme
 *       akışı localization servisinin ayakta olmasına bağımlı kılınmaz.</li>
 *   <li><b>Token propagasyonu:</b> localization-service JWT beklediği için
 *       mevcut isteğin {@code Authorization} header'ı aynen iletilir.</li>
 *   <li><b>Kısa timeout:</b> 2 sn connect/read — admin ekranını bloklamaz.</li>
 * </ul>
 */
@Slf4j
@Component
public class HttpLocalizationUsageClient implements LocalizationUsageClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public HttpLocalizationUsageClient(
            @Value("${wms.services.localization.base-url:http://localhost:8082}") String baseUrl) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public Optional<CountryUsage> fetchUsage(Long countryId) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders());

            ResponseEntity<JsonNode> addresses = restTemplate.exchange(
                    baseUrl + "/api/addresses?countryId={id}&page=0&size=1",
                    HttpMethod.GET, entity, JsonNode.class, countryId);

            long addressCount = Optional.ofNullable(addresses.getBody())
                    .map(b -> b.path("totalElements").asLong(0))
                    .orElse(0L);

            ResponseEntity<JsonNode> template = restTemplate.exchange(
                    baseUrl + "/api/addresses/templates/{id}",
                    HttpMethod.GET, entity, JsonNode.class, countryId);

            boolean templateExists = Optional.ofNullable(template.getBody())
                    .map(b -> b.isArray() && b.size() > 0)
                    .orElse(false);

            return Optional.of(new CountryUsage(addressCount, templateExists));
        } catch (Exception e) {
            log.warn("[GeoAdmin] localization-service kullanım kontrolü başarısız. countryId={} hata={}",
                    countryId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Mevcut HTTP isteğinin Authorization header'ını iletir (JWT propagasyonu). */
    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String auth = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (auth != null) {
                headers.set(HttpHeaders.AUTHORIZATION, auth);
            }
        }
        return headers;
    }
}

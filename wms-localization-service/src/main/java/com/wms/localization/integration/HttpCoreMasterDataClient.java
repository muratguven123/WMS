package com.wms.localization.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

/**
 * Core-service cascade endpoint'lerinden master data varlık kontrolü.
 */
@Slf4j
@Component
public class HttpCoreMasterDataClient implements CoreMasterDataClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public HttpCoreMasterDataClient(
            @Value("${wms.services.core.base-url:http://localhost:8081}") String baseUrl) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public boolean hasMasterData(Long countryId, String source) {
        try {
            String url = switch (source) {
                case "STATE" -> baseUrl + "/api/address/states?countryId={id}";
                case "CITY" -> baseUrl + "/api/address/cities?countryId={id}";
                case "DISTRICT", "NEIGHBORHOOD" -> null;
                default -> null;
            };
            if (url == null) {
                return true;
            }
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {},
                    countryId);
            List<Map<String, Object>> body = response.getBody();
            return body != null && !body.isEmpty();
        } catch (Exception e) {
            log.warn("[TemplateAdmin] core master data kontrolü başarısız. countryId={} source={} hata={}",
                    countryId, source, e.getMessage());
            return true;
        }
    }

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

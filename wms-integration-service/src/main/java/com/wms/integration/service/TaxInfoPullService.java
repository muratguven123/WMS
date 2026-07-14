package com.wms.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.integration.adapter.ErpAdapter;
import com.wms.integration.adapter.ErpAdapterFactory;
import com.wms.integration.adapter.dto.TaxInfoDto;
import com.wms.integration.entity.IntegrationJob;
import com.wms.integration.entity.IntegrationLog;
import com.wms.integration.entity.LocationIntegrationConfig;
import com.wms.integration.entity.enums.IntegrationStatus;
import com.wms.integration.outbox.OutboxMessageTypes;
import com.wms.integration.repository.IntegrationJobRepository;
import com.wms.integration.repository.IntegrationLogRepository;
import com.wms.integration.repository.LocationIntegrationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ERP'den vergi bilgisi çeker, IntegrationLog yazar ve finance master data'ya iletir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxInfoPullService {

    private final LocationIntegrationConfigRepository configRepository;
    private final ErpAdapterFactory erpAdapterFactory;
    private final IntegrationJobRepository jobRepository;
    private final IntegrationLogRepository logRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${wms.finance-service.url:http://localhost:8083}")
    private String financeServiceUrl;

    @Value("${tax-info-pull.default-country-id:1}")
    private Long defaultCountryId;

    @Transactional
    public int pullAllActiveLocations() {
        List<LocationIntegrationConfig> configs = configRepository.findAllActiveWithSystem();
        int imported = 0;
        for (LocationIntegrationConfig config : configs) {
            imported += pullForLocation(config);
        }
        return imported;
    }

    @Transactional
    public int pullForLocationId(Long locationId) {
        LocationIntegrationConfig config = configRepository.findActiveByLocationId(locationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active ERP config for locationId=" + locationId));
        return pullForLocation(config);
    }

    private int pullForLocation(LocationIntegrationConfig config) {
        Long locationId = config.getLocationId();
        IntegrationJob job = jobRepository.findByCodeAndIsActiveTrue(OutboxMessageTypes.TAX_INFO_PULL)
                .orElseThrow(() -> new IllegalStateException(
                        "IntegrationJob TAX_INFO_PULL not found"));

        try {
            ErpAdapter adapter = erpAdapterFactory.getAdapter(locationId);
            List<TaxInfoDto> rates = adapter.fetchTaxInfo();
            String payload = objectMapper.writeValueAsString(rates);

            int written = 0;
            for (TaxInfoDto tax : rates) {
                if (pushToFinance(tax)) {
                    written++;
                }
            }

            IntegrationLog logEntry = IntegrationLog.builder()
                    .locationIntegrationConfig(config)
                    .integrationJob(job)
                    .status(IntegrationStatus.SUCCESS)
                    .requestPayload(payload)
                    .responsePayload("imported=" + written)
                    .externalReference("TAX_PULL-" + locationId)
                    .retryCount(0)
                    .build();
            logRepository.save(logEntry);

            log.info("[TaxInfoPull] locationId={} fetched={} imported={}",
                    locationId, rates.size(), written);
            return written;
        } catch (Exception ex) {
            log.error("[TaxInfoPull] locationId={} failed: {}", locationId, ex.getMessage(), ex);
            IntegrationLog fail = IntegrationLog.builder()
                    .locationIntegrationConfig(config)
                    .integrationJob(job)
                    .status(IntegrationStatus.FAILED)
                    .errorMessage(ex.getMessage())
                    .retryCount(0)
                    .build();
            logRepository.save(fail);
            return 0;
        }
    }

    private boolean pushToFinance(TaxInfoDto tax) {
        try {
            Long countryId = mapCountryCode(tax.getCountryCode());
            Map<String, Object> body = new HashMap<>();
            body.put("taxTypeCode", normalizeTaxType(tax.getTaxTypeCode()));
            body.put("rate", tax.getRate());
            body.put("countryId", countryId);
            body.put("locationId", tax.getLocationId());
            body.put("validFrom", tax.getValidFrom());
            body.put("validTo", tax.getValidTo());

            restTemplate.exchange(
                    financeServiceUrl + "/api/taxes/rates/from-erp",
                    HttpMethod.POST,
                    new HttpEntity<>(body),
                    Map.class);
            return true;
        } catch (Exception ex) {
            log.warn("[TaxInfoPull] finance import failed taxType={}: {}",
                    tax.getTaxTypeCode(), ex.getMessage());
            return false;
        }
    }

    private Long mapCountryCode(String countryCode) {
        if (countryCode == null) {
            return defaultCountryId;
        }
        return switch (countryCode.toUpperCase()) {
            case "TR" -> 1L;
            case "DE" -> 2L;
            default -> defaultCountryId;
        };
    }

    /** ERP kodları (KDV_STANDART) finance TaxType koduna (KDV) yaklaştırılır. */
    private String normalizeTaxType(String code) {
        if (code == null) {
            return "KDV";
        }
        if (code.startsWith("KDV")) {
            return "KDV";
        }
        return code;
    }
}

package com.wms.integration.service;

import com.wms.events.DomainEvent;
import com.wms.integration.entity.IntegrationSystem;
import com.wms.integration.entity.LocationIntegrationConfig;
import com.wms.integration.entity.enums.ConnectionType;
import com.wms.integration.repository.IntegrationSystemRepository;
import com.wms.integration.repository.LocationIntegrationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationProvisioningService {

    private final LocationIntegrationConfigRepository configRepository;
    private final IntegrationSystemRepository integrationSystemRepository;

    @Transactional
    public void provisionFromEvent(DomainEvent event) {
        if (event.locationId() == null) {
            return;
        }
        if (configRepository.findActiveByLocationId(event.locationId()).isPresent()) {
            return;
        }

        IntegrationSystem mockSystem = integrationSystemRepository.findByCodeAndIsActiveTrue("MOCK")
                .orElse(null);
        if (mockSystem == null) {
            log.warn("MOCK integration system not found; skipping config for locationId={}", event.locationId());
            return;
        }

        LocationIntegrationConfig config = LocationIntegrationConfig.builder()
                .locationId(event.locationId())
                .integrationSystem(mockSystem)
                .connectionType(ConnectionType.REST)
                .connectionParams(Map.of("mock", true, "baseUrl", "http://localhost/mock"))
                .build();
        configRepository.save(config);
        log.info("LocationIntegrationConfig created for locationId={}", event.locationId());
    }
}

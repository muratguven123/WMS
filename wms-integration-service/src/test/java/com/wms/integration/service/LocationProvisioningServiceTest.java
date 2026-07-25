package com.wms.integration.service;

import com.wms.events.DomainEvent;
import com.wms.events.EventType;
import com.wms.integration.entity.IntegrationSystem;
import com.wms.integration.entity.LocationIntegrationConfig;
import com.wms.integration.repository.IntegrationSystemRepository;
import com.wms.integration.repository.LocationIntegrationConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LocationProvisioningService} birim testleri.
 *
 * <p>LOCATION_PROVISIONED event'inden otomatik MOCK config üretimini ve tüm erken-çıkış
 * dallarını (locationId yok / config zaten var / MOCK sistem yok) doğrular.
 * Mockito ile izole — Docker gerektirmez.
 */
@ExtendWith(MockitoExtension.class)
class LocationProvisioningServiceTest {

    @Mock
    private LocationIntegrationConfigRepository configRepository;
    @Mock
    private IntegrationSystemRepository integrationSystemRepository;

    @InjectMocks
    private LocationProvisioningService service;

    private static DomainEvent eventForLocation(Long locationId) {
        return DomainEvent.of(EventType.LOCATION_PROVISIONED, 1L, locationId, null);
    }

    @Test
    @DisplayName("locationId null ise hiçbir şey yapılmaz")
    void nullLocation_noop() {
        service.provisionFromEvent(eventForLocation(null));

        verify(configRepository, never()).save(any());
        verify(integrationSystemRepository, never()).findByCodeAndIsActiveTrue(any());
    }

    @Test
    @DisplayName("config zaten varsa yeni config oluşturulmaz")
    void existingConfig_noop() {
        when(configRepository.findActiveByLocationId(50L))
                .thenReturn(Optional.of(new LocationIntegrationConfig()));

        service.provisionFromEvent(eventForLocation(50L));

        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("MOCK sistem yoksa config oluşturulmaz")
    void noMockSystem_noop() {
        when(configRepository.findActiveByLocationId(60L)).thenReturn(Optional.empty());
        when(integrationSystemRepository.findByCodeAndIsActiveTrue("MOCK")).thenReturn(Optional.empty());

        service.provisionFromEvent(eventForLocation(60L));

        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("config yok + MOCK sistem var ise yeni config kaydedilir")
    void createsConfig() {
        IntegrationSystem mock = IntegrationSystem.builder().code("MOCK").name("Mock ERP").build();
        when(configRepository.findActiveByLocationId(70L)).thenReturn(Optional.empty());
        when(integrationSystemRepository.findByCodeAndIsActiveTrue("MOCK")).thenReturn(Optional.of(mock));

        service.provisionFromEvent(eventForLocation(70L));

        ArgumentCaptor<LocationIntegrationConfig> captor =
                ArgumentCaptor.forClass(LocationIntegrationConfig.class);
        verify(configRepository).save(captor.capture());
        assertThat(captor.getValue().getLocationId()).isEqualTo(70L);
        assertThat(captor.getValue().getIntegrationSystem().getCode()).isEqualTo("MOCK");
    }
}

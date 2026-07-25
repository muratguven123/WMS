package com.wms.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.integration.adapter.dto.CustomerAccountDto;
import com.wms.integration.entity.IntegrationJob;
import com.wms.integration.entity.IntegrationLog;
import com.wms.integration.entity.LocationIntegrationConfig;
import com.wms.integration.entity.OutboxMessage;
import com.wms.integration.outbox.OutboxMessageTypes;
import com.wms.integration.outbox.OutboxPublisherService;
import com.wms.integration.repository.IntegrationJobRepository;
import com.wms.integration.repository.IntegrationLogRepository;
import com.wms.integration.repository.LocationIntegrationConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ErpScenarioIntegrationService} birim testleri.
 *
 * <p>Atomik enqueue akışını (IntegrationLog → Outbox → çapraz referans), config/job
 * bulunamadığında IllegalStateException dallarını ve convenience API'nin doğru jobCode
 * ile delege etmesini doğrular. Gerçek ObjectMapper + mock repository'ler — Docker gerektirmez.
 */
@ExtendWith(MockitoExtension.class)
class ErpScenarioIntegrationServiceTest {

    @Mock
    private OutboxPublisherService outboxPublisher;
    @Mock
    private IntegrationLogRepository integrationLogRepository;
    @Mock
    private IntegrationJobRepository integrationJobRepository;
    @Mock
    private LocationIntegrationConfigRepository configRepository;
    @org.mockito.Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ErpScenarioIntegrationService service;

    private void stubHappyDeps(Long outboxId) {
        when(configRepository.findActiveByLocationId(any()))
                .thenReturn(Optional.of(new LocationIntegrationConfig()));
        when(integrationJobRepository.findByCodeAndIsActiveTrue(any()))
                .thenReturn(Optional.of(new IntegrationJob()));
        when(integrationLogRepository.save(any(IntegrationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        OutboxMessage outbox = mock(OutboxMessage.class);
        when(outbox.getId()).thenReturn(outboxId);
        when(outboxPublisher.saveOutboxMessage(any(), any(), any(), any(), any()))
                .thenReturn(outbox);
    }

    @Test
    @DisplayName("enqueue — log iki kez kaydedilir ve outbox id çapraz referanslanır")
    void enqueue_atomicWriteAndCrossReference() {
        stubHappyDeps(999L);

        service.enqueue("CustomerAccount", 5L, "CUSTOMER_SYNC", 10L, Map.of("k", "v"));

        ArgumentCaptor<IntegrationLog> logCaptor = ArgumentCaptor.forClass(IntegrationLog.class);
        verify(integrationLogRepository, times(2)).save(logCaptor.capture());
        // İkinci kayıtta outbox id set edilmiş olmalı.
        assertThat(logCaptor.getAllValues().get(1).getOutboxMessageId()).isEqualTo(999L);

        verify(outboxPublisher).saveOutboxMessage(
                eq("CustomerAccount"), eq(5L), eq("CUSTOMER_SYNC"), eq(10L), any());
    }

    @Test
    @DisplayName("enqueue — aktif config yoksa IllegalStateException, outbox'a yazılmaz")
    void enqueue_noConfig_throws() {
        when(configRepository.findActiveByLocationId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enqueue("CustomerAccount", 5L, "CUSTOMER_SYNC", 10L, Map.of("k", "v")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active ERP config");

        verify(outboxPublisher, never()).saveOutboxMessage(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("enqueue — job bulunamazsa IllegalStateException")
    void enqueue_noJob_throws() {
        when(configRepository.findActiveByLocationId(any()))
                .thenReturn(Optional.of(new LocationIntegrationConfig()));
        when(integrationJobRepository.findByCodeAndIsActiveTrue(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enqueue("CustomerAccount", 5L, "CUSTOMER_SYNC", 10L, Map.of("k", "v")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");

        verify(outboxPublisher, never()).saveOutboxMessage(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("enqueueCustomerAccount — CUSTOMER_SYNC jobCode'u ve deterministik aggregateId ile delege eder")
    void enqueueCustomerAccount_delegates() {
        stubHappyDeps(1000L);
        CustomerAccountDto dto = CustomerAccountDto.builder()
                .companyId(1L)
                .locationId(10L)
                .customerCode("CUST-1")
                .name("Test Cari")
                .build();

        service.enqueueCustomerAccount(dto);

        long expectedAggregateId = Math.abs((long) "CUST-1".hashCode());
        verify(outboxPublisher).saveOutboxMessage(
                eq("CustomerAccount"), eq(expectedAggregateId),
                eq(OutboxMessageTypes.CUSTOMER_SYNC), eq(10L), any());
    }
}

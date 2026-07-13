package com.wms.integration.service;

import com.wms.integration.api.dto.RetryResponse;
import com.wms.integration.entity.IntegrationLog;
import com.wms.integration.entity.OutboxMessage;
import com.wms.integration.entity.enums.IntegrationStatus;
import com.wms.integration.entity.enums.OutboxStatus;
import com.wms.integration.repository.IntegrationLogRepository;
import com.wms.integration.repository.OutboxMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationRetryServiceTest {

    @Mock
    private IntegrationLogRepository logRepository;

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @InjectMocks
    private IntegrationRetryService retryService;

    @Test
    void forceRetry_resetsFailedMaxRetriesOutbox() {
        Long logId = 1L;
        Long outboxId = 1L;

        IntegrationLog integrationLog = IntegrationLog.builder()
                .status(IntegrationStatus.FAILED)
                .outboxMessageId(outboxId)
                .build();
        integrationLog.setId(logId);

        OutboxMessage outboxMessage = OutboxMessage.builder()
                .status(OutboxStatus.FAILED_MAX_RETRIES)
                .retryCount(3)
                .errorMessage("ERP timeout")
                .build();
        outboxMessage.setId(outboxId);

        when(logRepository.findById(logId)).thenReturn(Optional.of(integrationLog));
        when(outboxMessageRepository.findById(outboxId)).thenReturn(Optional.of(outboxMessage));

        RetryResponse response = retryService.forceRetry(logId);

        assertThat(response.getLogId()).isEqualTo(logId);
        assertThat(response.getOutboxMessageId()).isEqualTo(outboxId);
        assertThat(outboxMessage.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outboxMessage.getRetryCount()).isZero();
        assertThat(outboxMessage.getErrorMessage()).isNull();
        assertThat(integrationLog.getStatus()).isEqualTo(IntegrationStatus.RETRYING);

        verify(outboxMessageRepository).save(outboxMessage);
        verify(logRepository).save(integrationLog);
    }

    @Test
    void forceRetry_whenStatusSuccess_rejects() {
        Long logId = 1L;
        IntegrationLog integrationLog = IntegrationLog.builder()
                .status(IntegrationStatus.SUCCESS)
                .build();
        integrationLog.setId(logId);

        when(logRepository.findById(logId)).thenReturn(Optional.of(integrationLog));

        assertThatThrownBy(() -> retryService.forceRetry(logId))
                .isInstanceOf(IntegrationRetryService.RetryNotAllowedException.class);
    }
}

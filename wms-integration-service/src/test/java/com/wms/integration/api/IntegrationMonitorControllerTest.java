package com.wms.integration.api;

import com.wms.integration.api.dto.IntegrationLogResponse;
import com.wms.integration.api.dto.RetryResponse;
import com.wms.integration.entity.enums.IntegrationStatus;
import com.wms.integration.repository.IntegrationLogRepository;
import com.wms.integration.repository.IntegrationLogRepository.StatusCountProjection;
import com.wms.integration.service.IntegrationLogQueryService;
import com.wms.integration.service.IntegrationLogQueryService.IntegrationLogNotFoundException;
import com.wms.integration.service.IntegrationRetryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IntegrationMonitorController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("IntegrationMonitorController")
class IntegrationMonitorControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private IntegrationLogQueryService logQueryService;
    @MockBean private IntegrationRetryService retryService;
    @MockBean private IntegrationLogRepository logRepository;

    @Test
    @DisplayName("GET /api/integrations/logs — sayfalı log listesi döner")
    void listLogs_returnsPage() throws Exception {
        Long logId = 1L;
        IntegrationLogResponse log = IntegrationLogResponse.builder()
                .id(logId)
                .locationId(1L)
                .status(IntegrationStatus.FAILED)
                .retryCount(2)
                .createdAt(OffsetDateTime.now())
                .build();

        when(logQueryService.queryLogs(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        mockMvc.perform(get("/api/integrations/logs")
                        .param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(logId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"));
    }

    @Test
    @DisplayName("GET /api/integrations/stats — son 24 saat istatistikleri döner")
    void getStats_returnsCounts() throws Exception {
        StatusCountProjection success = mock(StatusCountProjection.class);
        when(success.getStatus()).thenReturn(IntegrationStatus.SUCCESS);
        when(success.getCount()).thenReturn(42L);

        StatusCountProjection failed = mock(StatusCountProjection.class);
        when(failed.getStatus()).thenReturn(IntegrationStatus.FAILED);
        when(failed.getCount()).thenReturn(3L);

        when(logRepository.countByStatusSince(any(OffsetDateTime.class)))
                .thenReturn(List.of(success, failed));

        mockMvc.perform(get("/api/integrations/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.SUCCESS").value(42))
                .andExpect(jsonPath("$.counts.FAILED").value(3))
                .andExpect(jsonPath("$.since").exists());
    }

    @Test
    @DisplayName("POST /api/integrations/logs/{logId}/retry — başarılı retry döner")
    void forceRetry_returnsOk() throws Exception {
        Long logId = 1L;
        Long outboxId = 1L;
        OffsetDateTime scheduledAt = OffsetDateTime.now();

        RetryResponse response = RetryResponse.builder()
                .logId(logId)
                .outboxMessageId(outboxId)
                .message("Retry scheduled")
                .scheduledAt(scheduledAt)
                .build();

        when(retryService.forceRetry(logId)).thenReturn(response);

        mockMvc.perform(post("/api/integrations/logs/{logId}/retry", logId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logId").value(logId.toString()))
                .andExpect(jsonPath("$.outboxMessageId").value(outboxId.toString()))
                .andExpect(jsonPath("$.message").value("Retry scheduled"));
    }

    @Test
    @DisplayName("POST /api/integrations/logs/{logId}/retry — log bulunamazsa 404 döner")
    void forceRetry_notFound_returns404() throws Exception {
        Long logId = 1L;

        when(retryService.forceRetry(eq(logId)))
                .thenThrow(new IntegrationLogNotFoundException(logId));

        mockMvc.perform(post("/api/integrations/logs/{logId}/retry", logId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("IntegrationLog not found: id=" + logId));
    }
}

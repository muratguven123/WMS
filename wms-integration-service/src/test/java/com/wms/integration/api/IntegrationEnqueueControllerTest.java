package com.wms.integration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.integration.adapter.dto.MovementDto;
import com.wms.integration.service.InventoryMovementIntegrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IntegrationEnqueueController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("IntegrationEnqueueController")
class IntegrationEnqueueControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private InventoryMovementIntegrationService movementIntegrationService;

    @Test
    @DisplayName("POST /api/integrations/movements — hareketi kuyruğa alır")
    void enqueueMovement_returnsAccepted() throws Exception {
        Long locationId = 1L;
        MovementDto movement = MovementDto.builder()
                .movementId(1L)
                .movementType("TRANSFER")
                .sku("SKU-1")
                .quantity(new BigDecimal("10"))
                .unit("EA")
                .movementDate(Instant.parse("2026-07-05T10:00:00Z"))
                .locationId(100L)
                .referenceDocumentNo("REF-001")
                .build();

        mockMvc.perform(post("/api/integrations/movements")
                        .param("locationId", locationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(movement)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.message").value("Movement enqueued for ERP sync via Outbox"));

        verify(movementIntegrationService).enqueueMovement(any(MovementDto.class), eq(locationId));
    }
}

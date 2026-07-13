package com.wms.inbound.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.dto.ReceiptResponse;
import com.wms.inbound.dto.StartReceiptRequest;
import com.wms.inbound.entity.enums.ReceiptStatus;
import com.wms.inbound.exception.GlobalExceptionHandler;
import com.wms.inbound.service.ReceiptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReceiptController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("ReceiptController")
class ReceiptControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ReceiptService receiptService;

    @Test
    @DisplayName("POST /api/inbound/receipts — mal kabul başlatır")
    void startReceipt_returnsCreated() throws Exception {
        Long receiptId = 1L;
        Long orderId = 1L;
        ReceiptResponse response = new ReceiptResponse(
                receiptId, orderId, "REC-001", 1L,
                LocalDateTime.now(), ReceiptStatus.QC_PENDING, List.of());

        when(receiptService.startReceipt(any())).thenReturn(response);

        StartReceiptRequest request = new StartReceiptRequest(
                orderId, "REC-001", 1L,
                List.of(new com.wms.inbound.dto.ReceiptItemRequest("SKU-1", new BigDecimal("10"))));

        mockMvc.perform(post("/api/inbound/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(receiptId.toString()))
                .andExpect(jsonPath("$.receiptNumber").value("REC-001"));

        verify(receiptService).startReceipt(any(StartReceiptRequest.class));
    }

    @Test
    @DisplayName("POST /api/inbound/receipts — eksik alanlarda 400 döner")
    void startReceipt_invalidRequest_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/inbound/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/inbound/receipts/{id}/approve — onaylar")
    void approveReceipt_returnsOk() throws Exception {
        Long receiptId = 1L;
        when(receiptService.approveReceipt(receiptId)).thenReturn(
                new ReceiptResponse(receiptId, 1L, "REC-001",
                        1L, LocalDateTime.now(), ReceiptStatus.APPROVED, List.of()));

        mockMvc.perform(post("/api/inbound/receipts/{receiptId}/approve", receiptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(receiptService).approveReceipt(receiptId);
    }
}

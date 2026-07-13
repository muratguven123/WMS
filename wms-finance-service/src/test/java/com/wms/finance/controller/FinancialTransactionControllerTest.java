package com.wms.finance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.finance.dto.CreateFinancialTransactionRequest;
import com.wms.finance.dto.FinancialTransactionDto;
import com.wms.finance.exception.GlobalExceptionHandler;
import com.wms.finance.service.FinancialTransactionService;
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
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinancialTransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("FinancialTransactionController")
class FinancialTransactionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private FinancialTransactionService transactionService;

    @Test
    @DisplayName("POST /api/finance/transactions — finansal işlem kaydeder")
    void record_returnsCreated() throws Exception {
        Long transactionId = 1L;
        Long companyId = 1L;
        Long locationId = 1L;
        Instant transactionDate = Instant.parse("2026-07-05T10:00:00Z");

        FinancialTransactionDto response = new FinancialTransactionDto(
                transactionId,
                companyId,
                locationId,
                null,
                "USD",
                new BigDecimal("1000.00"),
                new BigDecimal("34.50"),
                "TRY",
                new BigDecimal("34500.00"),
                transactionDate,
                false);

        when(transactionService.recordTransaction(any())).thenReturn(response);

        CreateFinancialTransactionRequest request = new CreateFinancialTransactionRequest(
                companyId, locationId, null, null,
                new BigDecimal("1000.00"), transactionDate, "SELLING");

        mockMvc.perform(post("/api/finance/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(transactionId.toString()))
                .andExpect(jsonPath("$.originalCurrencyCode").value("USD"));

        verify(transactionService).recordTransaction(any(CreateFinancialTransactionRequest.class));
    }

    @Test
    @DisplayName("POST /api/finance/transactions — eksik alanlarda 400 döner")
    void record_invalidRequest_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/finance/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}

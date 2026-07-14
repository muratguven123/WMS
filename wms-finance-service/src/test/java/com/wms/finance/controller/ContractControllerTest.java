package com.wms.finance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.finance.dto.ContractDto;
import com.wms.finance.dto.CreateContractRequest;
import com.wms.finance.exception.GlobalExceptionHandler;
import com.wms.finance.service.ContractService;
import com.wms.finance.dto.ContractFixedRateRequest;
import com.wms.finance.entity.enums.RateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContractController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("ContractController")
class ContractControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ContractService contractService;

    @Test
    @DisplayName("POST /api/finance/contracts — sözleşme oluşturur")
    void createContract_returnsCreated() throws Exception {
        Long contractId = 1L;
        Long customerId = 1L;
        Long currencyId = 1L;
        LocalDateTime startDate = LocalDateTime.of(2026, 7, 5, 0, 0);

        ContractDto response = ContractDto.builder()
                .id(contractId)
                .customerId(customerId)
                .currencyId(currencyId)
                .currencyCode("EUR")
                .contractCode("CNT-001")
                .startDate(startDate)
                .endDate(null)
                .build();

        when(contractService.createContract(any())).thenReturn(response);

        CreateContractRequest request = new CreateContractRequest(
                customerId, currencyId, "CNT-001", startDate, null);

        mockMvc.perform(post("/api/finance/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(contractId.toString()))
                .andExpect(jsonPath("$.contractCode").value("CNT-001"));

        verify(contractService).createContract(any(CreateContractRequest.class));
    }

    @Test
    @DisplayName("POST /api/finance/contracts — eksik alanlarda 400 döner")
    void createContract_invalidRequest_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/finance/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/finance/contracts/{contractId}/fixed-rates — sabit kurları listeler")
    void getFixedRates_returnsList() throws Exception {
        Long contractId = 1L;
        com.wms.finance.dto.ContractFixedRateDto dto = com.wms.finance.dto.ContractFixedRateDto.builder()
                .id(10L)
                .contractId(contractId)
                .sourceCurrencyId(2L)
                .sourceCurrencyCode("USD")
                .targetCurrencyId(3L)
                .targetCurrencyCode("TRY")
                .rate(new java.math.BigDecimal("32.500000"))
                .rateType(RateType.SELLING)
                .validFrom(LocalDate.of(2026, 7, 1))
                .active(true)
                .build();

        when(contractService.getFixedRates(contractId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/finance/contracts/{contractId}/fixed-rates", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].rate").value("32.5"));
    }

    @Test
    @DisplayName("POST /api/finance/contracts/{contractId}/fixed-rates — sabit kur ekler")
    void addFixedRate_returnsCreated() throws Exception {
        Long contractId = 1L;
        ContractFixedRateRequest request = new ContractFixedRateRequest(
                2L, 3L, new java.math.BigDecimal("32.500000"), RateType.SELLING, LocalDate.of(2026, 7, 1), null, true);

        com.wms.finance.dto.ContractFixedRateDto dto = com.wms.finance.dto.ContractFixedRateDto.builder()
                .id(10L)
                .contractId(contractId)
                .sourceCurrencyId(2L)
                .sourceCurrencyCode("USD")
                .targetCurrencyId(3L)
                .targetCurrencyCode("TRY")
                .rate(new java.math.BigDecimal("32.500000"))
                .rateType(RateType.SELLING)
                .validFrom(LocalDate.of(2026, 7, 1))
                .active(true)
                .build();

        when(contractService.addFixedRate(eq(contractId), any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/finance/contracts/{contractId}/fixed-rates", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.rate").value("32.5"));
    }
}

package com.wms.finance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.finance.dto.CustomerExchangeRateDto;
import com.wms.finance.dto.CustomerExchangeRateRequest;
import com.wms.finance.entity.enums.AuditActionType;
import com.wms.finance.entity.enums.RateType;
import com.wms.finance.exception.GlobalExceptionHandler;
import com.wms.finance.service.CustomerRateService;
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
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerRateController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("CustomerRateController")
class CustomerRateControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CustomerRateService customerRateService;

    @Test
    @DisplayName("POST /api/finance/customers/{customerId}/rates — müşteri özel kuru ekler/günceller")
    void upsertCustomerRate_returnsCreated() throws Exception {
        Long customerId = 1L;
        LocalDate date = LocalDate.of(2026, 7, 3);
        CustomerExchangeRateRequest request = new CustomerExchangeRateRequest("EUR", "TRY", date, RateType.SELLING, new BigDecimal("35.000000"));

        CustomerExchangeRateDto dto = CustomerExchangeRateDto.builder()
                .id(100L)
                .customerId(customerId)
                .sourceCurrency("EUR")
                .targetCurrency("TRY")
                .rateDate(date)
                .rateType(RateType.SELLING)
                .rate(new BigDecimal("35.000000"))
                .action(AuditActionType.INSERT)
                .build();

        when(customerRateService.upsertCustomerRate(eq(customerId), any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/finance/customers/{customerId}/rates", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.rate").value("35.0"));

        verify(customerRateService).upsertCustomerRate(eq(customerId), any(), any());
    }

    @Test
    @DisplayName("GET /api/finance/customers/{customerId}/rates — tarih bazlı müşteri özel kuru sorgular")
    void getCustomerRate_returnsOk() throws Exception {
        Long customerId = 1L;
        LocalDate date = LocalDate.of(2026, 7, 3);

        CustomerExchangeRateDto dto = CustomerExchangeRateDto.builder()
                .id(100L)
                .customerId(customerId)
                .sourceCurrency("EUR")
                .targetCurrency("TRY")
                .rateDate(date)
                .rateType(RateType.SELLING)
                .rate(new BigDecimal("35.000000"))
                .build();

        when(customerRateService.getCustomerRateQuery(eq(customerId), eq("EUR"), eq("TRY"), eq(date), eq(RateType.SELLING)))
                .thenReturn(dto);

        mockMvc.perform(get("/api/finance/customers/{customerId}/rates", customerId)
                        .param("sourceCurrency", "EUR")
                        .param("targetCurrency", "TRY")
                        .param("rateDate", "2026-07-03")
                        .param("rateType", "SELLING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.rate").value("35.0"));
    }
}

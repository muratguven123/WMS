package com.wms.finance.controller;

import com.wms.finance.dto.ExchangeRateDto;
import com.wms.finance.exception.GlobalExceptionHandler;
import com.wms.finance.service.CurrencyConversionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CurrencyRateLookupController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("CurrencyRateLookupController")
class CurrencyRateLookupControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private CurrencyConversionService currencyConversionService;

    @Test
    @DisplayName("GET /api/rates/lookup — döviz kuru döner")
    void lookupRate_returnsOk() throws Exception {
        LocalDate rateDate = LocalDate.of(2026, 7, 5);

        ExchangeRateDto response = new ExchangeRateDto(
                "USD", "TRY", new BigDecimal("34.50"),
                rateDate, "SELLING", "TCMB", false);

        when(currencyConversionService.lookupRate(
                eq("USD"), eq("TRY"), eq(rateDate), eq("SELLING")))
                .thenReturn(response);

        mockMvc.perform(get("/api/rates/lookup")
                        .param("fromCurrency", "USD")
                        .param("toCurrency", "TRY")
                        .param("rateDate", "2026-07-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCurrency").value("USD"))
                .andExpect(jsonPath("$.targetCurrency").value("TRY"))
                .andExpect(jsonPath("$.rate").value(34.50));

        verify(currencyConversionService).lookupRate("USD", "TRY", rateDate, "SELLING");
    }

    @Test
    @DisplayName("GET /api/rates/lookup — eksik parametrelerde 400 döner")
    void lookupRate_missingParams_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/rates/lookup")
                        .param("fromCurrency", "USD"))
                .andExpect(status().isBadRequest());
    }
}

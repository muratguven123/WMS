package com.wms.finance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.finance.dto.TaxRateResponse;
import com.wms.finance.dto.TaxRateUpdateRequest;
import com.wms.finance.dto.TaxRateVersionResult;
import com.wms.finance.exception.GlobalExceptionHandler;
import com.wms.finance.service.ErpTaxImportService;
import com.wms.finance.service.TaxQueryService;
import com.wms.finance.service.TaxRateVersioningService;
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
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaxRateController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("TaxRateController")
class TaxRateControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TaxRateVersioningService versioningService;
    @MockBean private TaxQueryService taxQueryService;
    @MockBean private ErpTaxImportService erpTaxImportService;

    @Test
    @DisplayName("POST /api/taxes/rates/update — vergi oranını günceller")
    void updateRate_returnsOk() throws Exception {
        Long taxRateId = 1L;
        LocalDate effectiveDate = LocalDate.now();

        TaxRateResponse expiredRate = new TaxRateResponse(
                taxRateId, "VAT", null, null, null, null, null,
                new BigDecimal("18.00"), effectiveDate.minusDays(1), effectiveDate.minusDays(1),
                false, LocalDateTime.now());

        TaxRateResponse newRate = new TaxRateResponse(
                1L, "VAT", null, null, null, null, null,
                new BigDecimal("20.00"), effectiveDate, null,
                true, LocalDateTime.now());

        when(versioningService.updateRate(any(), isNull()))
                .thenReturn(new TaxRateVersionResult(expiredRate, newRate));

        TaxRateUpdateRequest request = new TaxRateUpdateRequest(
                taxRateId, new BigDecimal("20.00"), effectiveDate);

        mockMvc.perform(post("/api/taxes/rates/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newRate.rate").value(20.00))
                .andExpect(jsonPath("$.expiredRate.active").value(false));

        verify(versioningService).updateRate(any(TaxRateUpdateRequest.class), isNull());
    }

    @Test
    @DisplayName("POST /api/taxes/rates/update — eksik alanlarda 400 döner")
    void updateRate_invalidRequest_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/taxes/rates/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}

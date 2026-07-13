package com.wms.finance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.finance.dto.ManualRateRequest;
import com.wms.finance.dto.ManualRateResponse;
import com.wms.finance.dto.TcmbSyncResponse;
import com.wms.finance.entity.enums.AuditActionType;
import com.wms.finance.entity.enums.RateType;
import com.wms.finance.exception.GlobalExceptionHandler;
import com.wms.finance.job.TcmbRateSyncJob;
import com.wms.finance.service.ActiveRateQueryService;
import com.wms.finance.service.ManualRateService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExchangeRateController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("ExchangeRateController")
class ExchangeRateControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ManualRateService manualRateService;
    @MockBean private ActiveRateQueryService activeRateQueryService;
    @MockBean private TcmbRateSyncJob tcmbRateSyncJob;

    @Test
    @DisplayName("GET /api/rates/active — aktif kur listesi döner")
    void listActiveRates_returnsOk() throws Exception {
        when(activeRateQueryService.listActiveRates("TRY", null, "SELLING"))
                .thenReturn(new com.wms.finance.dto.ActiveRateListDto(
                        "TRY", LocalDate.of(2026, 7, 7), "SELLING", List.of(), null));

        mockMvc.perform(get("/api/rates/active").param("rateType", "SELLING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("TRY"));
    }

    @Test
    @DisplayName("POST /api/rates/sync/tcmb — senkronizasyon sonucu döner")
    void syncTcmbRates_returnsOk() throws Exception {
        when(tcmbRateSyncJob.syncTcmbRatesWithResult())
                .thenReturn(new TcmbSyncResponse(true, 2, 1, 0, LocalDate.of(2026, 7, 7)));

        mockMvc.perform(post("/api/rates/sync/tcmb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.inserted").value(2));
    }

    @Test
    @DisplayName("POST /api/rates/manual — manuel kur ekler")
    void upsertManualRate_insert_returnsCreated() throws Exception {
        Long rateId = 1L;
        LocalDate rateDate = LocalDate.of(2026, 7, 5);

        ManualRateResponse response = new ManualRateResponse(
                rateId, "USD", "TRY", rateDate, RateType.SELLING,
                new BigDecimal("34.50"), AuditActionType.INSERT, null);

        when(manualRateService.upsert(any(), isNull())).thenReturn(response);

        ManualRateRequest request = new ManualRateRequest(
                "USD", "TRY", rateDate, RateType.SELLING, new BigDecimal("34.50"));

        mockMvc.perform(post("/api/rates/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(rateId.toString()))
                .andExpect(jsonPath("$.action").value("INSERT"));

        verify(manualRateService).upsert(any(ManualRateRequest.class), isNull());
    }

    @Test
    @DisplayName("POST /api/rates/manual — eksik alanlarda 400 döner")
    void upsertManualRate_invalidRequest_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/rates/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}

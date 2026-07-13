package com.wms.inbound.controller;

import com.wms.inbound.dto.PutawayRecommendationResponse;
import com.wms.inbound.service.PutawayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PutawayController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PutawayController")
class PutawayControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private PutawayService putawayService;

    @Test
    @DisplayName("GET putaway önerileri listesini döner")
    void recommendPutaway_returnsList() throws Exception {
        Long receiptId = 1L;
        Long locationId = 1L;
        var recommendation = new PutawayRecommendationResponse(
                "PROD-1", locationId, "A-01-01", new BigDecimal("42.5"));

        when(putawayService.recommendForReceipt(receiptId)).thenReturn(List.of(recommendation));

        mockMvc.perform(get("/api/inbound/receipts/{receiptId}/putaway", receiptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productCode").value("PROD-1"))
                .andExpect(jsonPath("$[0].storageLocationId").value(locationId.toString()));

        verify(putawayService).recommendForReceipt(receiptId);
    }

    @Test
    @DisplayName("GET item putaway — öneri yoksa 204 döner")
    void recommendPutawayForItem_noRecommendation_returnsNoContent() throws Exception {
        Long receiptId = 1L;
        when(putawayService.recommendForItem(receiptId, "PROD-X")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/inbound/receipts/{receiptId}/items/{productCode}/putaway",
                        receiptId, "PROD-X"))
                .andExpect(status().isNoContent());
    }
}

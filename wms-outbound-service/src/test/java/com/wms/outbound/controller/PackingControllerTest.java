package com.wms.outbound.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.dto.CloseBoxRequest;
import com.wms.outbound.dto.CloseBoxResponse;
import com.wms.outbound.dto.PackingVerifyRequest;
import com.wms.outbound.dto.PackingVerifyResponse;
import com.wms.outbound.service.PackingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PackingController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PackingController")
class PackingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private PackingService packingService;

    @Test
    @DisplayName("POST /verify — paketleme doğrulaması yapar")
    void verifyPackingItem_returnsOk() throws Exception {
        Long pickingListId = 1L;
        when(packingService.verifyPackingItem(any())).thenReturn(
                new PackingVerifyResponse(
                        1L, "SKU-1", new BigDecimal("1"),
                        new BigDecimal("1"), "VERIFIED", null));

        PackingVerifyRequest request = new PackingVerifyRequest(
                pickingListId, "BARCODE-1", new BigDecimal("1"));

        mockMvc.perform(post("/api/packing/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    @DisplayName("POST /close-box — kutu kapatır")
    void closeBox_returnsOk() throws Exception {
        Long pickingListId = 1L;
        when(packingService.closeBox(pickingListId)).thenReturn(
                new CloseBoxResponse(pickingListId, "SSCC-123", List.of(), "CLOSED"));

        mockMvc.perform(post("/api/packing/close-box")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CloseBoxRequest(pickingListId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sscc").value("SSCC-123"));
    }
}

package com.wms.outbound.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.dto.CreatePickingListRequest;
import com.wms.outbound.dto.PickingListResponse;
import com.wms.outbound.entity.enums.PickingListStatus;
import com.wms.outbound.service.PickingRoutingService;
import com.wms.outbound.service.PickingTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PickingController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PickingController")
class PickingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private PickingRoutingService pickingRoutingService;
    @MockBean private PickingTaskService pickingTaskService;

    @Test
    @DisplayName("POST /api/picking/lists — toplama listesi oluşturur")
    void createPickingList_returnsCreated() throws Exception {
        Long warehouseId = 1L;
        Long userId = 1L;
        Long orderId = 1L;
        var response = new PickingListResponse(
                1L, warehouseId, 1L, userId, null, null,
                PickingListStatus.PENDING, LocalDateTime.now(), List.of());

        when(pickingRoutingService.createPickingList(
                eq(List.of(orderId)), eq(warehouseId), eq(userId))).thenReturn(response);

        CreatePickingListRequest request = new CreatePickingListRequest(
                List.of(orderId), warehouseId, userId);

        mockMvc.perform(post("/api/picking/lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}

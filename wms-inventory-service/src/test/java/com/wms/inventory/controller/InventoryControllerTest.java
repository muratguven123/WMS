package com.wms.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.dto.AllocateStockRequest;
import com.wms.inventory.dto.AllocatedStockDto;
import com.wms.inventory.dto.InternalMoveRequest;
import com.wms.inventory.dto.InventoryAdjustRequest;
import com.wms.inventory.dto.InventoryIssueLine;
import com.wms.inventory.dto.InventoryIssueRequest;
import com.wms.inventory.exception.GlobalExceptionHandler;
import com.wms.inventory.service.InventoryAllocationService;
import com.wms.inventory.service.InventoryIssueService;
import com.wms.inventory.service.InventoryMoveService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("InventoryController")
class InventoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private InventoryMoveService inventoryMoveService;
    @MockBean private InventoryAllocationService inventoryAllocationService;
    @MockBean private InventoryIssueService inventoryIssueService;

    @Test
    @DisplayName("POST /allocate — stok tahsis eder")
    void allocateStock_returnsAllocations() throws Exception {
        Long locationId = 1L;
        var allocation = new AllocatedStockDto(locationId, "LOT-1", new BigDecimal("5.0000"));
        when(inventoryAllocationService.allocateStock(
                eq("SKU-1"), any(), eq("FIFO"), eq(locationId)))
                .thenReturn(List.of(allocation));

        AllocateStockRequest request = new AllocateStockRequest(
                "SKU-1", new BigDecimal("5.0000"), "FIFO", locationId);

        mockMvc.perform(post("/api/inventory/allocate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].storageLocationId").value(locationId.toString()));
    }

    @Test
    @DisplayName("POST /move — iç transfer çağrısı yapar")
    void moveStock_returnsOk() throws Exception {
        Long from = 1L;
        Long to = 1L;
        InternalMoveRequest request = new InternalMoveRequest(
                from, to, "SKU-1", new BigDecimal("3"), "LOT-1");

        mockMvc.perform(post("/api/inventory/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(inventoryMoveService).moveStock(any(InternalMoveRequest.class));
    }

    @Test
    @DisplayName("POST /count/adjust — sayım düzeltmesi yapar")
    void adjustStock_returnsOk() throws Exception {
        InventoryAdjustRequest request = new InventoryAdjustRequest(
                1L, "SKU-1", null, null,
                new BigDecimal("10"), 1L, 1L);

        mockMvc.perform(post("/api/inventory/count/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(inventoryMoveService).adjustStock(any(InventoryAdjustRequest.class));
    }

    @Test
    @DisplayName("POST /issue — sevkiyat çıkışı yapar")
    void issueStock_returnsOk() throws Exception {
        InventoryIssueRequest request = new InventoryIssueRequest(
                1L, "SHIP-001", 1L, 1L,
                List.of(new InventoryIssueLine("SKU-1", new BigDecimal("1"))));

        mockMvc.perform(post("/api/inventory/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(inventoryIssueService).issueStock(any(InventoryIssueRequest.class));
    }
}

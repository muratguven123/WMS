package com.wms.finance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.finance.dto.OrderRequestDto;
import com.wms.finance.dto.OrderResponseDto;
import com.wms.finance.exception.GlobalExceptionHandler;
import com.wms.finance.service.OrderService;
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

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("OrderController")
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private OrderService orderService;

    @Test
    @DisplayName("POST /api/finance/orders — sipariş oluşturur")
    void createOrder_returnsCreated() throws Exception {
        Long orderId = 1L;
        Long customerId = 1L;
        Long currencyId = 1L;
        Instant orderDate = Instant.parse("2026-07-05T10:00:00Z");

        OrderResponseDto response = OrderResponseDto.builder()
                .orderId(orderId)
                .customerId(customerId)
                .currencyId(currencyId)
                .currencyCode("TRY")
                .amount(new BigDecimal("100.00"))
                .orderDate(orderDate)
                .build();

        when(orderService.createOrder(any())).thenReturn(response);

        OrderRequestDto request = new OrderRequestDto(
                customerId, currencyId, new BigDecimal("100.00"), orderDate, null);

        mockMvc.perform(post("/api/finance/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.currencyCode").value("TRY"));

        verify(orderService).createOrder(any(OrderRequestDto.class));
    }

    @Test
    @DisplayName("POST /api/finance/orders — eksik alanlarda 400 döner")
    void createOrder_invalidRequest_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/finance/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}

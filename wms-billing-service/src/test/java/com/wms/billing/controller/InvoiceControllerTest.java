package com.wms.billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.billing.domain.enums.InvoiceStatus;
import com.wms.billing.dto.CreateInvoiceRequest;
import com.wms.billing.dto.InvoiceItemInputDto;
import com.wms.billing.dto.InvoiceResponse;
import com.wms.billing.exception.GlobalExceptionHandler;
import com.wms.billing.service.InvoiceService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("InvoiceController")
class InvoiceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private InvoiceService invoiceService;

    @Test
    @DisplayName("POST /api/billing/invoices — fatura oluşturur")
    void create_returnsCreated() throws Exception {
        CreateInvoiceRequest request = CreateInvoiceRequest.builder()
                .customerId(1L)
                .invoiceCurrency("EUR")
                .exchangeRateDate(LocalDate.of(2026, 7, 4))
                .items(List.of(InvoiceItemInputDto.builder()
                        .itemDescription("Hizmet")
                        .quantity(new BigDecimal("1"))
                        .unitPriceOriginal(new BigDecimal("100"))
                        .discountOriginal(BigDecimal.ZERO)
                        .taxRate(new BigDecimal("20"))
                        .build()))
                .build();

        InvoiceResponse response = InvoiceResponse.builder()
                .id(1L)
                .invoiceNumber("INV-10-20260704-001")
                .customerId(1L)
                .locationId(10L)
                .invoiceCurrency("EUR")
                .accountingCurrency("TRY")
                .exchangeRateDate(LocalDate.of(2026, 7, 4))
                .exchangeRateValue(new BigDecimal("36.85"))
                .items(List.of())
                .subtotalOriginal(new BigDecimal("100"))
                .taxAmountOriginal(new BigDecimal("20"))
                .grandTotalOriginal(new BigDecimal("120"))
                .grandTotalAccounting(new BigDecimal("4422.00"))
                .status(InvoiceStatus.DRAFT)
                .preview(false)
                .build();

        when(invoiceService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/billing/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-10-20260704-001"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(invoiceService).create(any());
    }

    @Test
    @DisplayName("POST /api/billing/invoices/{id}/approve — onaylar")
    void approve_returnsOk() throws Exception {
        InvoiceResponse response = InvoiceResponse.builder()
                .id(1L)
                .invoiceNumber("INV-1")
                .customerId(1L)
                .locationId(10L)
                .invoiceCurrency("EUR")
                .accountingCurrency("TRY")
                .exchangeRateDate(LocalDate.of(2026, 7, 4))
                .exchangeRateValue(new BigDecimal("36.85"))
                .items(List.of())
                .subtotalOriginal(new BigDecimal("100"))
                .taxAmountOriginal(new BigDecimal("20"))
                .grandTotalOriginal(new BigDecimal("120"))
                .grandTotalAccounting(new BigDecimal("4422.00"))
                .status(InvoiceStatus.APPROVED)
                .preview(false)
                .build();

        when(invoiceService.approve(1L)).thenReturn(response);

        mockMvc.perform(post("/api/billing/invoices/1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(invoiceService).approve(eq(1L));
    }
}

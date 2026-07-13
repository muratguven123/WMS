package com.wms.billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.billing.domain.entity.ExchangeDifferenceLog;
import com.wms.billing.domain.entity.Invoice;
import com.wms.billing.domain.enums.InvoiceStatus;
import com.wms.billing.dto.ExchangeDifferenceRequest;
import com.wms.billing.dto.ExchangeDifferenceResponse;
import com.wms.billing.exception.GlobalExceptionHandler;
import com.wms.billing.exception.InvoiceNotFoundException;
import com.wms.billing.mapper.InvoiceMapper;
import com.wms.billing.service.ExchangeDifferenceService;
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
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExchangeDifferenceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, InvoiceMapper.class})
@DisplayName("ExchangeDifferenceController")
class ExchangeDifferenceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ExchangeDifferenceService exchangeDifferenceService;
    @MockBean private InvoiceService invoiceService;

    @Test
    @DisplayName("POST exchange-difference — kur farkı kaydı oluşturur")
    void calculate_returnsCreated() throws Exception {
        Invoice invoice = Invoice.builder().id(1L).locationId(10L).status(InvoiceStatus.APPROVED).build();
        ExchangeDifferenceLog log = ExchangeDifferenceLog.builder()
                .id(5L)
                .invoice(invoice)
                .calculationDate(LocalDateTime.now())
                .originalPaidAmount(new BigDecimal("120"))
                .rateAtPayment(new BigDecimal("37.00"))
                .exchangeDifferenceAmount(new BigDecimal("18.0000"))
                .actionTaken("EXCHANGE_GAIN")
                .build();

        ExchangeDifferenceRequest request = ExchangeDifferenceRequest.builder()
                .paidAmountOriginal(new BigDecimal("120"))
                .rateAtPayment(new BigDecimal("37.00"))
                .build();

        when(exchangeDifferenceService.calculateAndLogExchangeDifference(
                eq(1L), eq(new BigDecimal("120")), eq(new BigDecimal("37.00"))))
                .thenReturn(log);

        mockMvc.perform(post("/api/billing/invoices/1/exchange-difference")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.invoiceId").value(1))
                .andExpect(jsonPath("$.actionTaken").value("EXCHANGE_GAIN"));

        verify(invoiceService).requireInvoiceForTenant(1L);
    }

    @Test
    @DisplayName("POST exchange-difference — fatura yoksa 404")
    void calculate_notFound_returns404() throws Exception {
        ExchangeDifferenceRequest request = ExchangeDifferenceRequest.builder()
                .paidAmountOriginal(new BigDecimal("120"))
                .rateAtPayment(new BigDecimal("37.00"))
                .build();

        doThrow(new InvoiceNotFoundException(99L))
                .when(invoiceService).requireInvoiceForTenant(99L);

        mockMvc.perform(post("/api/billing/invoices/99/exchange-difference")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Invoice Not Found"));
    }
}

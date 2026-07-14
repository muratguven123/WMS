package com.wms.integration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.integration.adapter.dto.*;
import com.wms.integration.service.InventoryMovementIntegrationService;
import com.wms.integration.service.ErpScenarioIntegrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IntegrationEnqueueController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("IntegrationEnqueueController")
class IntegrationEnqueueControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private InventoryMovementIntegrationService movementIntegrationService;
    @MockBean private ErpScenarioIntegrationService erpScenarioIntegrationService;

    @Test
    @DisplayName("POST /api/integrations/movements — hareketi kuyruğa alır")
    void enqueueMovement_returnsAccepted() throws Exception {
        Long locationId = 1L;
        MovementDto movement = MovementDto.builder()
                .movementId(1L)
                .movementType("TRANSFER")
                .sku("SKU-1")
                .quantity(new BigDecimal("10"))
                .unit("EA")
                .movementDate(Instant.parse("2026-07-05T10:00:00Z"))
                .locationId(100L)
                .referenceDocumentNo("REF-001")
                .build();

        mockMvc.perform(post("/api/integrations/movements")
                        .param("locationId", locationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(movement)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.message").value("Movement enqueued for ERP sync via Outbox"));

        verify(movementIntegrationService).enqueueMovement(any(MovementDto.class), eq(locationId));
    }

    @Test
    @DisplayName("POST /api/integrations/customers — cari hesabı kuyruğa alır")
    void enqueueCustomer_returnsAccepted() throws Exception {
        CustomerAccountDto customer = CustomerAccountDto.builder()
                .companyId(1L)
                .locationId(10L)
                .customerCode("CUST-01")
                .name("Customer Name")
                .build();

        mockMvc.perform(post("/api/integrations/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customer)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.message").value("CustomerAccount enqueued for ERP sync via Outbox"));

        verify(erpScenarioIntegrationService).enqueueCustomerAccount(any(CustomerAccountDto.class));
    }

    @Test
    @DisplayName("POST /api/integrations/purchase-orders — satın alma siparişini kuyruğa alır")
    void enqueuePurchaseOrder_returnsAccepted() throws Exception {
        PurchaseOrderDto po = PurchaseOrderDto.builder()
                .companyId(1L)
                .locationId(10L)
                .orderNumber("PO-001")
                .orderDate(LocalDate.now())
                .customerCode("CUST-01")
                .build();

        mockMvc.perform(post("/api/integrations/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(po)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.message").value("PurchaseOrder enqueued for ERP sync via Outbox"));

        verify(erpScenarioIntegrationService).enqueuePurchaseOrder(any(PurchaseOrderDto.class));
    }

    @Test
    @DisplayName("POST /api/integrations/sales-orders — satış siparişini kuyruğa alır")
    void enqueueSalesOrder_returnsAccepted() throws Exception {
        SalesOrderDto so = SalesOrderDto.builder()
                .companyId(1L)
                .locationId(10L)
                .orderNumber("SO-001")
                .orderDate(LocalDate.now())
                .customerCode("CUST-01")
                .build();

        mockMvc.perform(post("/api/integrations/sales-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(so)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.message").value("SalesOrder enqueued for ERP sync via Outbox"));

        verify(erpScenarioIntegrationService).enqueueSalesOrder(any(SalesOrderDto.class));
    }

    @Test
    @DisplayName("POST /api/integrations/returns — iade bildirimini kuyruğa alır")
    void enqueueReturn_returnsAccepted() throws Exception {
        ReturnNoticeDto returnNotice = ReturnNoticeDto.builder()
                .companyId(1L)
                .locationId(10L)
                .referenceOrderNumber("SO-001")
                .build();

        mockMvc.perform(post("/api/integrations/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnNotice)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.message").value("ReturnNotice enqueued for ERP sync via Outbox"));

        verify(erpScenarioIntegrationService).enqueueReturnNotice(any(ReturnNoticeDto.class));
    }

    @Test
    @DisplayName("POST /api/integrations/counts — sayım sonucunu kuyruğa alır")
    void enqueueCount_returnsAccepted() throws Exception {
        CountResultDto countResult = CountResultDto.builder()
                .companyId(1L)
                .locationId(10L)
                .countId(99L)
                .countDate(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/integrations/counts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(countResult)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.message").value("CountResult enqueued for ERP sync via Outbox"));

        verify(erpScenarioIntegrationService).enqueueCountResult(any(CountResultDto.class));
    }

    @Test
    @DisplayName("POST /api/integrations/vouchers — muhasebe fişini kuyruğa alır")
    void enqueueVoucher_returnsAccepted() throws Exception {
        AccountingVoucherDto voucher = AccountingVoucherDto.builder()
                .companyId(1L)
                .locationId(10L)
                .voucherType("MAHSUP")
                .voucherDate(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/integrations/vouchers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voucher)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.message").value("AccountingVoucher enqueued for ERP sync via Outbox"));

        verify(erpScenarioIntegrationService).enqueueAccountingVoucher(any(AccountingVoucherDto.class));
    }
}

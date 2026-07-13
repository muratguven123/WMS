package com.wms.integration.api;

import com.wms.integration.service.ErpReceiptIntegrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ErpReceiptIntegrationController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ErpReceiptIntegrationController")
class ErpReceiptIntegrationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ErpReceiptIntegrationService erpReceiptIntegrationService;

    @Test
    @DisplayName("POST /api/integration/erp/receipts — mal kabul onayını kuyruğa alır")
    void receiveReceiptApproval_returnsAccepted() throws Exception {
        String payload = "{\"receiptId\":\"abc-123\"}";

        mockMvc.perform(post("/api/integration/erp/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.message").value("Receipt approval enqueued for ERP sync via Outbox"));

        verify(erpReceiptIntegrationService).enqueueReceipt(payload);
    }
}

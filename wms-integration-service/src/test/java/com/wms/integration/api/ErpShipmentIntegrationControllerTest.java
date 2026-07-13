package com.wms.integration.api;

import com.wms.integration.service.ErpShipmentIntegrationService;
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

@WebMvcTest(ErpShipmentIntegrationController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ErpShipmentIntegrationController")
class ErpShipmentIntegrationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ErpShipmentIntegrationService erpShipmentIntegrationService;

    @Test
    @DisplayName("POST /api/integration/erp/shipments — sevkiyat çıkışını kuyruğa alır")
    void receiveShipmentDispatch_returnsAccepted() throws Exception {
        String payload = "{\"shipmentId\":\"ship-456\"}";

        mockMvc.perform(post("/api/integration/erp/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.message").value("Shipment dispatch enqueued for ERP sync via Outbox"));

        verify(erpShipmentIntegrationService).enqueueShipment(payload);
    }
}

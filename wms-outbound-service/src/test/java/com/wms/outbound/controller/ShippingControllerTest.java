package com.wms.outbound.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.dto.CreateShipmentBoxRequest;
import com.wms.outbound.dto.CreateShipmentRequest;
import com.wms.outbound.entity.Shipment;
import com.wms.outbound.entity.enums.ShipmentStatus;
import com.wms.outbound.service.ShipmentService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShippingController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ShippingController")
class ShippingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private ShipmentService shipmentService;

    @Test
    @DisplayName("POST /shipments — sevkiyat oluşturur")
    void createShipment_returnsCreated() throws Exception {
        Shipment shipment = Shipment.builder()
                .id(1L)
                .shipmentNumber("SHIP-001")
                .status(ShipmentStatus.PENDING)
                .build();
        when(shipmentService.createShipment(any())).thenReturn(shipment);

        CreateShipmentRequest request = new CreateShipmentRequest(
                "SHIP-001", 1L, 1L, "DHL",
                BigDecimal.TEN, List.of(new CreateShipmentBoxRequest(1L, "BOX-SSCC-1")));

        mockMvc.perform(post("/api/shipping/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipmentNumber").value("SHIP-001"));
    }

    @Test
    @DisplayName("POST /{id}/dispatch — sevkiyatı çıkarır")
    void dispatch_returnsOk() throws Exception {
        Long shipmentId = 1L;
        Shipment dispatched = Shipment.builder()
                .id(shipmentId)
                .shipmentNumber("SHIP-001")
                .status(ShipmentStatus.DISPATCHED)
                .build();
        when(shipmentService.dispatch(shipmentId)).thenReturn(dispatched);

        mockMvc.perform(post("/api/shipping/{shipmentId}/dispatch", shipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));
    }
}

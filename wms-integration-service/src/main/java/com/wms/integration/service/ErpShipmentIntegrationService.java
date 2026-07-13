package com.wms.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.integration.adapter.dto.ShipmentDispatchDto;
import com.wms.integration.outbox.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * wms-outbound-service'ten gelen sevkiyat çıkışlarını entegrasyon outbox'ına yazar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpShipmentIntegrationService {

    private final OutboxPublisherService outboxPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enqueueShipment(String rawJsonPayload) {
        try {
            ShipmentDispatchDto shipment = objectMapper.readValue(rawJsonPayload, ShipmentDispatchDto.class);

            Long locationId = shipment.getWarehouseLocationId() != null
                    ? shipment.getWarehouseLocationId()
                    : shipment.getCompanyId();

            if (locationId == null) {
                throw new IllegalArgumentException("warehouseLocationId or companyId is required");
            }

            outboxPublisher.saveOutboxMessage(
                    "Shipment",
                    shipment.getShipmentId(),
                    "SHIPMENT_SYNC",
                    locationId,
                    shipment);

            log.info("Shipment dispatch queued for ERP sync: shipmentNumber={}, locationId={}",
                    shipment.getShipmentNumber(), locationId);

        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid shipment dispatch payload: " + ex.getMessage(), ex);
        }
    }
}

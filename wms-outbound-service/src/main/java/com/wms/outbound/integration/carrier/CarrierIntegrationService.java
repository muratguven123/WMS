package com.wms.outbound.integration.carrier;

import com.wms.outbound.dto.CarrierResponseDto;
import com.wms.outbound.dto.ShippingAddressDto;
import com.wms.outbound.entity.Shipment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarrierIntegrationService {

    private final List<CarrierAdapter> adapters;

    public CarrierResponseDto requestShippingLabel(Shipment shipment, ShippingAddressDto address) {
        String carrierCode = shipment.getCarrierCode();
        log.info("Processing shipping label request for shipment: {} with carrier: {}", shipment.getShipmentNumber(), carrierCode);

        if (carrierCode == null || carrierCode.isBlank()) {
            throw new IllegalArgumentException("Carrier code must not be null or blank");
        }

        CarrierAdapter selectedAdapter = adapters.stream()
                .filter(adapter -> adapter.supports(carrierCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No integration adapter found for carrier: " + carrierCode));

        return selectedAdapter.requestShippingLabel(shipment, address);
    }
}

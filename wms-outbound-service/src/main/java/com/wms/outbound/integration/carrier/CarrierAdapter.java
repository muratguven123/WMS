package com.wms.outbound.integration.carrier;

import com.wms.outbound.dto.CarrierResponseDto;
import com.wms.outbound.dto.ShippingAddressDto;
import com.wms.outbound.entity.Shipment;

public interface CarrierAdapter {
    CarrierResponseDto requestShippingLabel(Shipment shipment, ShippingAddressDto address);
    boolean supports(String carrierCode);
}

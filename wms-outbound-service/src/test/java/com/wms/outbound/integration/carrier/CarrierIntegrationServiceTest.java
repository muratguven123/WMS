package com.wms.outbound.integration.carrier;

import com.wms.outbound.dto.CarrierResponseDto;
import com.wms.outbound.dto.ShippingAddressDto;
import com.wms.outbound.entity.Shipment;
import com.wms.outbound.entity.enums.ShipmentStatus;
import com.wms.outbound.integration.carrier.impl.DhlCarrierAdapter;
import com.wms.outbound.integration.carrier.impl.YurticiCarrierAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarrierIntegrationServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    @SuppressWarnings("rawtypes")
    private Mono mono;

    private CarrierIntegrationService carrierIntegrationService;
    private Shipment shipment;
    private ShippingAddressDto address;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        lenient().when(webClientBuilder.build()).thenReturn(webClient);
        
        lenient().when(webClient.post()).thenReturn(requestBodyUriSpec);
        lenient().when(requestBodyUriSpec.contentType(any())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        lenient().when(responseSpec.bodyToMono(any(Class.class))).thenReturn(mono);

        DhlCarrierAdapter dhlAdapter = new DhlCarrierAdapter(webClientBuilder, "http://localhost:8089/api/mock/dhl");
        YurticiCarrierAdapter yurticiAdapter = new YurticiCarrierAdapter(webClientBuilder, "http://localhost:8089/api/mock/yurtici");

        carrierIntegrationService = new CarrierIntegrationService(List.of(dhlAdapter, yurticiAdapter));

        shipment = Shipment.builder()
                .id(1L)
                .shipmentNumber("SH-2026-0001")
                .companyId(1L)
                .status(ShipmentStatus.PENDING)
                .totalBoxes(2)
                .totalWeight(new BigDecimal("12.5000"))
                .build();

        address = ShippingAddressDto.builder()
                .recipientName("Ahmet Yilmaz")
                .street("Merkez Mah. Ataturk Cad. No:12")
                .city("Istanbul")
                .postalCode("34000")
                .countryCode("TR")
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestShippingLabel_withDhlCarrier_shouldRouteToDhlAdapterAndFallbackOnException() {
        when(mono.block()).thenThrow(new RuntimeException("Connection timed out"));
        shipment.setCarrierCode("DHL");

        CarrierResponseDto response = carrierIntegrationService.requestShippingLabel(shipment, address);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCarrierCode()).isEqualTo("DHL");
        assertThat(response.getTrackingNumber()).startsWith("DHL-SH-2026-0001-");
        assertThat(response.getLabelBase64()).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestShippingLabel_withDhlCarrier_shouldReturnSuccessWhenWebClientSucceeds() {
        DhlCarrierAdapter.DhlResponse dhlResponse = new DhlCarrierAdapter.DhlResponse("DHL-REAL-12345", "real-base64-pdf-content");
        when(mono.block()).thenReturn(dhlResponse);
        shipment.setCarrierCode("DHL");

        CarrierResponseDto response = carrierIntegrationService.requestShippingLabel(shipment, address);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCarrierCode()).isEqualTo("DHL");
        assertThat(response.getTrackingNumber()).isEqualTo("DHL-REAL-12345");
        assertThat(response.getLabelBase64()).isEqualTo("real-base64-pdf-content");
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestShippingLabel_withYurticiCarrier_shouldRouteToYurticiAdapterAndFallbackOnException() {
        when(mono.block()).thenThrow(new RuntimeException("Connection error"));
        shipment.setCarrierCode("YURTICI");

        CarrierResponseDto response = carrierIntegrationService.requestShippingLabel(shipment, address);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCarrierCode()).isEqualTo("YURTICI");
        assertThat(response.getTrackingNumber()).startsWith("YRT-SH-2026-0001-");
        assertThat(response.getLabelBase64()).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestShippingLabel_withYurticiCarrier_shouldReturnSuccessWhenWebClientSucceeds() {
        String soapResponseXml = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                "   <soapenv:Body>" +
                "      <createShipmentResponse>" +
                "         <trackingNumber>YRT-REAL-98765</trackingNumber>" +
                "         <labelBase64>yurtici-real-base64-pdf</labelBase64>" +
                "      </createShipmentResponse>" +
                "   </soapenv:Body>" +
                "</soapenv:Envelope>";
        when(mono.block()).thenReturn(soapResponseXml);
        shipment.setCarrierCode("YURTICI");

        CarrierResponseDto response = carrierIntegrationService.requestShippingLabel(shipment, address);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCarrierCode()).isEqualTo("YURTICI");
        assertThat(response.getTrackingNumber()).isEqualTo("YRT-REAL-98765");
        assertThat(response.getLabelBase64()).isEqualTo("yurtici-real-base64-pdf");
    }

    @Test
    void requestShippingLabel_withUnsupportedCarrier_shouldThrowIllegalArgumentException() {
        shipment.setCarrierCode("FEDEX");

        assertThatThrownBy(() -> carrierIntegrationService.requestShippingLabel(shipment, address))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No integration adapter found for carrier: FEDEX");
    }

    @Test
    void requestShippingLabel_withNullCarrier_shouldThrowIllegalArgumentException() {
        shipment.setCarrierCode(null);

        assertThatThrownBy(() -> carrierIntegrationService.requestShippingLabel(shipment, address))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Carrier code must not be null or blank");
    }
}

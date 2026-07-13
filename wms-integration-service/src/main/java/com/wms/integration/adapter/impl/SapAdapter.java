package com.wms.integration.adapter.impl;

import com.wms.integration.adapter.ErpAdapter;
import com.wms.integration.adapter.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * SAP REST API adaptörü.
 *
 * <p>Spring WebClient üzerinden SAP OData/REST uç noktalarına JSON istekleri
 * gönderir. Her metod kendi hata yönetimini yapar; ağ/HTTP hataları
 * {@link ErpResponse#failure} olarak sarılır ve üst katmana (Outbox Worker)
 * aktarılır.
 *
 * <p><b>Mock notu:</b> Gerçek SAP URL'leri enjekte edilene kadar uç noktalar
 * placeholder olarak bırakılmıştır; davranış ve hata akışı üretimle aynıdır.
 */
@Slf4j
@Component("sapAdapter")
@RequiredArgsConstructor
public class SapAdapter implements ErpAdapter {

    private final WebClient webClient;

    @Value("${erp.sap.base-url:http://sap-mock-host/api/wms}")
    private String sapBaseUrl;

    // -----------------------------------------------------------------------
    // ErpAdapter implementation
    // -----------------------------------------------------------------------

    @Override
    public ErpResponse sendMaterialCard(MaterialDto material) {
        log.info("[SAP] sendMaterialCard -> sku={}, locationId={}",
                material.getSku(), material.getLocationId());
        try {
            Map<?, ?> sapResponse = webClient.post()
                    .uri(sapBaseUrl + "/materials")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(toSapMaterialPayload(material))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String docNumber = extractDocNumber(sapResponse);
            log.info("[SAP] Material card created, SAP doc={}", docNumber);
            return ErpResponse.success(docNumber, "Material card sent to SAP");

        } catch (WebClientResponseException ex) {
            log.error("[SAP] sendMaterialCard HTTP error: status={}, body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            return ErpResponse.failure(String.valueOf(ex.getStatusCode().value()),
                    "SAP returned HTTP " + ex.getStatusCode() + ": " + ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("[SAP] sendMaterialCard unexpected error", ex);
            return ErpResponse.failure("SAP_ERR", ex.getMessage());
        }
    }

    @Override
    public ErpResponse sendInventoryMovement(MovementDto movement) {
        log.info("[SAP] sendInventoryMovement -> movementId={}, type={}",
                movement.getMovementId(), movement.getMovementType());
        try {
            Map<?, ?> sapResponse = webClient.post()
                    .uri(sapBaseUrl + "/inventory-movements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(toSapMovementPayload(movement))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String docNumber = extractDocNumber(sapResponse);
            log.info("[SAP] Inventory movement sent, SAP doc={}", docNumber);
            return ErpResponse.success(docNumber, "Inventory movement sent to SAP");

        } catch (WebClientResponseException ex) {
            log.error("[SAP] sendInventoryMovement HTTP error: status={}", ex.getStatusCode());
            return ErpResponse.failure(String.valueOf(ex.getStatusCode().value()),
                    "SAP HTTP error: " + ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("[SAP] sendInventoryMovement unexpected error", ex);
            return ErpResponse.failure("SAP_ERR", ex.getMessage());
        }
    }

    @Override
    public ErpResponse sendInvoice(InvoiceDto invoice) {
        log.info("[SAP] sendInvoice -> invoiceNumber={}, currency={}",
                invoice.getInvoiceNumber(), invoice.getCurrencyCode());
        try {
            Map<?, ?> sapResponse = webClient.post()
                    .uri(sapBaseUrl + "/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(toSapInvoicePayload(invoice))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String docNumber = extractDocNumber(sapResponse);
            log.info("[SAP] Invoice sent, SAP doc={}", docNumber);
            return ErpResponse.success(docNumber, "Invoice sent to SAP");

        } catch (WebClientResponseException ex) {
            log.error("[SAP] sendInvoice HTTP error: status={}", ex.getStatusCode());
            return ErpResponse.failure(String.valueOf(ex.getStatusCode().value()),
                    "SAP HTTP error: " + ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("[SAP] sendInvoice unexpected error", ex);
            return ErpResponse.failure("SAP_ERR", ex.getMessage());
        }
    }

    @Override
    public List<ExchangeRateDto> fetchExchangeRates() {
        log.info("[SAP] fetchExchangeRates");
        try {
            // SAP TCURR tablosundan kur çekme (mock endpoint)
            List<?> rawRates = webClient.get()
                    .uri(sapBaseUrl + "/exchange-rates")
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .collectList()
                    .block();

            return rawRates == null ? List.of() : rawRates.stream()
                    .map(r -> {
                        Map<?, ?> m = (Map<?, ?>) r;
                        return ExchangeRateDto.builder()
                                .fromCurrency((String) m.get("fromCurrency"))
                                .toCurrency((String) m.get("toCurrency"))
                                .rate(new java.math.BigDecimal(m.get("rate").toString()))
                                .rateDate(java.time.LocalDate.parse(m.get("rateDate").toString()))
                                .build();
                    })
                    .toList();

        } catch (Exception ex) {
            log.error("[SAP] fetchExchangeRates error", ex);
            return List.of();
        }
    }

    // -----------------------------------------------------------------------
    // Private — payload builders (SAP'a özgü alan mapping)
    // -----------------------------------------------------------------------

    private Map<String, Object> toSapMaterialPayload(MaterialDto dto) {
        return Map.of(
                "MaterialCode",    dto.getSku(),
                "MaterialName",    dto.getName(),
                "BaseUnit",        dto.getUnit(),
                "GrossWeight",     dto.getUnitWeight(),
                "EANCode",         dto.getBarcode() != null ? dto.getBarcode() : "",
                "Plant",           dto.getLocationId().toString()
        );
    }

    private Map<String, Object> toSapMovementPayload(MovementDto dto) {
        return Map.of(
                "GoodsMovementCode", mapMovementType(dto.getMovementType()),
                "MaterialCode",      dto.getSku(),
                "Quantity",          dto.getQuantity(),
                "BaseUnit",          dto.getUnit(),
                "PostingDate",       dto.getMovementDate().toString(),
                "ReferenceDocument", dto.getReferenceDocumentNo() != null
                                         ? dto.getReferenceDocumentNo() : ""
        );
    }

    private Map<String, Object> toSapInvoicePayload(InvoiceDto dto) {
        return Map.of(
                "BillingDocument",   dto.getInvoiceNumber(),
                "BillingDate",       dto.getInvoiceDate().toString(),
                "Customer",          dto.getPartnerCode(),
                "TransactionCurrency", dto.getCurrencyCode(),
                "NetAmount",         dto.getTotalAmountForeign(),
                "LocalAmount",       dto.getTotalAmountLocal(),
                "ExchangeRate",      dto.getLockedExchangeRate()
        );
    }

    /** SAP hareket tipi mapping (örnek — gerçek MIGO hareketleriyle genişletilmeli). */
    private String mapMovementType(String movementType) {
        return switch (movementType) {
            case "RECEIPT"    -> "101"; // GR for PO
            case "SHIPMENT"   -> "601"; // GI for Delivery
            case "TRANSFER"   -> "311"; // Transfer posting
            case "ADJUSTMENT" -> "551"; // Scrapping
            default           -> "999";
        };
    }

    private String extractDocNumber(Map<?, ?> response) {
        if (response == null) return "UNKNOWN";
        Object val = response.get("documentNumber");
        if (val == null) val = response.get("docNumber");
        if (val == null) val = response.get("id");
        return val != null ? val.toString() : "UNKNOWN";
    }
}

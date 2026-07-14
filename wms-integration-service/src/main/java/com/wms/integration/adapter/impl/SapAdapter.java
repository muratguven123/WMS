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
    // İş isteri 7.4 — ek senaryolar (en kritik ikisi: cari hesap + muhasebe fişi;
    // kalanlar default NOT_IMPLEMENTED olarak arayüzden gelir)
    // -----------------------------------------------------------------------

    @Override
    public ErpResponse sendCustomerAccount(CustomerAccountDto customerAccount) {
        log.info("[SAP] sendCustomerAccount -> customerCode={}, companyId={}",
                customerAccount.getCustomerCode(), customerAccount.getCompanyId());
        try {
            Map<?, ?> sapResponse = webClient.post()
                    .uri(sapBaseUrl + "/business-partners")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(toSapBusinessPartnerPayload(customerAccount))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String docNumber = extractDocNumber(sapResponse);
            log.info("[SAP] Business partner created, SAP doc={}", docNumber);
            return ErpResponse.success(docNumber, "Customer account sent to SAP");

        } catch (WebClientResponseException ex) {
            log.error("[SAP] sendCustomerAccount HTTP error: status={}, body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            return ErpResponse.failure(String.valueOf(ex.getStatusCode().value()),
                    "SAP HTTP error: " + ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("[SAP] sendCustomerAccount unexpected error", ex);
            return ErpResponse.failure("SAP_ERR", ex.getMessage());
        }
    }

    @Override
    public ErpResponse sendAccountingVoucher(AccountingVoucherDto voucher) {
        log.info("[SAP] sendAccountingVoucher -> voucherType={}, lines={}",
                voucher.getVoucherType(),
                voucher.getLines() != null ? voucher.getLines().size() : 0);
        try {
            Map<?, ?> sapResponse = webClient.post()
                    .uri(sapBaseUrl + "/accounting-documents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(toSapAccountingDocumentPayload(voucher))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String docNumber = extractDocNumber(sapResponse);
            log.info("[SAP] Accounting document posted, SAP doc={}", docNumber);
            return ErpResponse.success(docNumber, "Accounting voucher sent to SAP");

        } catch (WebClientResponseException ex) {
            log.error("[SAP] sendAccountingVoucher HTTP error: status={}, body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            return ErpResponse.failure(String.valueOf(ex.getStatusCode().value()),
                    "SAP HTTP error: " + ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("[SAP] sendAccountingVoucher unexpected error", ex);
            return ErpResponse.failure("SAP_ERR", ex.getMessage());
        }
    }

    @Override
    public ErpResponse sendGoodsReceipt(ReceiptApprovalDto receipt) {
        return postJson("/goods-receipts", toSapGoodsReceiptPayload(receipt),
                "Goods receipt sent to SAP", "sendGoodsReceipt");
    }

    @Override
    public ErpResponse sendShipmentDispatch(ShipmentDispatchDto shipment) {
        return postJson("/shipment-dispatches", toSapShipmentPayload(shipment),
                "Shipment dispatch sent to SAP", "sendShipmentDispatch");
    }

    @Override
    public ErpResponse sendPurchaseOrder(PurchaseOrderDto purchaseOrder) {
        return postJson("/purchase-orders", purchaseOrder,
                "Purchase order sent to SAP", "sendPurchaseOrder");
    }

    @Override
    public ErpResponse sendSalesOrder(SalesOrderDto salesOrder) {
        return postJson("/sales-orders", salesOrder,
                "Sales order sent to SAP", "sendSalesOrder");
    }

    @Override
    public ErpResponse sendReturnNotice(ReturnNoticeDto returnNotice) {
        return postJson("/return-notices", returnNotice,
                "Return notice sent to SAP", "sendReturnNotice");
    }

    @Override
    public ErpResponse sendCountResult(CountResultDto countResult) {
        return postJson("/count-results", countResult,
                "Count result sent to SAP", "sendCountResult");
    }

    @Override
    public List<TaxInfoDto> fetchTaxInfo() {
        log.info("[SAP] fetchTaxInfo");
        try {
            List<?> raw = webClient.get()
                    .uri(sapBaseUrl + "/tax-info")
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .collectList()
                    .block();
            if (raw == null) {
                return List.of();
            }
            return raw.stream().map(r -> {
                Map<?, ?> m = (Map<?, ?>) r;
                return TaxInfoDto.builder()
                        .companyId(m.get("companyId") != null ? Long.valueOf(m.get("companyId").toString()) : null)
                        .locationId(m.get("locationId") != null ? Long.valueOf(m.get("locationId").toString()) : null)
                        .taxTypeCode((String) m.get("taxTypeCode"))
                        .rate(m.get("rate") != null ? new java.math.BigDecimal(m.get("rate").toString()) : null)
                        .countryCode((String) m.get("countryCode"))
                        .validFrom(m.get("validFrom") != null
                                ? java.time.LocalDate.parse(m.get("validFrom").toString()) : null)
                        .build();
            }).toList();
        } catch (Exception ex) {
            log.error("[SAP] fetchTaxInfo error", ex);
            return List.of();
        }
    }

    private ErpResponse postJson(String path, Object body, String successMsg, String opName) {
        try {
            Map<?, ?> sapResponse = webClient.post()
                    .uri(sapBaseUrl + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            String docNumber = extractDocNumber(sapResponse);
            return ErpResponse.success(docNumber, successMsg);
        } catch (WebClientResponseException ex) {
            log.error("[SAP] {} HTTP error: status={}", opName, ex.getStatusCode());
            return ErpResponse.failure(String.valueOf(ex.getStatusCode().value()),
                    "SAP HTTP error: " + ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("[SAP] {} unexpected error", opName, ex);
            return ErpResponse.failure("SAP_ERR", ex.getMessage());
        }
    }

    private Map<String, Object> toSapGoodsReceiptPayload(ReceiptApprovalDto dto) {
        return Map.of(
                "ReceiptNumber", dto.getReceiptNumber() != null ? dto.getReceiptNumber() : "",
                "Plant", dto.getWarehouseLocationId() != null ? dto.getWarehouseLocationId().toString() : "",
                "Items", dto.getApprovedItems() != null ? dto.getApprovedItems() : List.of()
        );
    }

    private Map<String, Object> toSapShipmentPayload(ShipmentDispatchDto dto) {
        return Map.of(
                "ShipmentNumber", dto.getShipmentNumber() != null ? dto.getShipmentNumber() : "",
                "Plant", dto.getWarehouseLocationId() != null ? dto.getWarehouseLocationId().toString() : "",
                "SsccNumbers", dto.getBoxSsccNumbers() != null ? dto.getBoxSsccNumbers() : List.of()
        );
    }

    // -----------------------------------------------------------------------
    // Private — payload builders (SAP'a özgü alan mapping)
    // -----------------------------------------------------------------------

    /** SAP Business Partner (BP) alan eşlemesi — cari hesap kartı. */
    private Map<String, Object> toSapBusinessPartnerPayload(CustomerAccountDto dto) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("BusinessPartner",      dto.getCustomerCode());
        payload.put("BusinessPartnerName",  dto.getName());
        payload.put("TaxNumber",            dto.getTaxNumber() != null ? dto.getTaxNumber() : "");
        payload.put("Currency",             dto.getCurrencyCode() != null ? dto.getCurrencyCode() : "");
        payload.put("Address",              dto.getAddress() != null ? dto.getAddress() : "");
        payload.put("CompanyCode",          dto.getCompanyId().toString());
        payload.put("Plant",                dto.getLocationId().toString());
        return payload;
    }

    /** SAP muhasebe belgesi (BAPI_ACC_DOCUMENT_POST benzeri) alan eşlemesi. */
    private Map<String, Object> toSapAccountingDocumentPayload(AccountingVoucherDto dto) {
        List<Map<String, Object>> items = dto.getLines() == null ? List.of()
                : dto.getLines().stream()
                        .map(line -> {
                            Map<String, Object> item = new java.util.LinkedHashMap<String, Object>();
                            item.put("GLAccount",    line.getAccountCode());
                            item.put("DebitAmount",  line.getDebit()  != null ? line.getDebit()  : java.math.BigDecimal.ZERO);
                            item.put("CreditAmount", line.getCredit() != null ? line.getCredit() : java.math.BigDecimal.ZERO);
                            item.put("Currency",     line.getCurrencyCode() != null ? line.getCurrencyCode() : "");
                            item.put("ExchangeRate", line.getExchangeRate() != null ? line.getExchangeRate() : java.math.BigDecimal.ONE);
                            return item;
                        })
                        .toList();

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("DocumentType",  mapVoucherType(dto.getVoucherType()));
        payload.put("PostingDate",   dto.getVoucherDate().toString());
        payload.put("CompanyCode",   dto.getCompanyId().toString());
        payload.put("Plant",         dto.getLocationId().toString());
        payload.put("Items",         items);
        return payload;
    }

    /** WMS fiş tipi → SAP belge tipi eşlemesi (örnek — müşteri projesine göre genişletilmeli). */
    private String mapVoucherType(String voucherType) {
        return switch (voucherType) {
            case "MAHSUP" -> "SA"; // G/L account document
            case "TAHSIL" -> "DZ"; // Customer payment
            case "TEDIYE" -> "KZ"; // Vendor payment
            default       -> "SA";
        };
    }

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

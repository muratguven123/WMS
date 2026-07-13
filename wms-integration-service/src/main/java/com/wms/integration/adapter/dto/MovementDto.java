package com.wms.integration.adapter.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ERP'ye gönderilecek stok hareketi verisi.
 */
@Value
@Builder
public class MovementDto {

    /** WMS stok hareketi ID */
    Long movementId;

    /** Hareket tipi: RECEIPT, SHIPMENT, TRANSFER, ADJUSTMENT */
    String movementType;

    /** SKU */
    String sku;

    /** Miktar */
    BigDecimal quantity;

    /** Birim */
    String unit;

    /** Hareket tarihi (UTC) */
    Instant movementDate;

    /** Kaynak lokasyon ID */
    Long locationId;

    /** Şirket ID — gerçek zamanlı entegrasyon olayının tenant topic'ini belirler. */
    Long companyId;

    /** Referans belge no (sipariş, transfer emri vb.) */
    String referenceDocumentNo;
}

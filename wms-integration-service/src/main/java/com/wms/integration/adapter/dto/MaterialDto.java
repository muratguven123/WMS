package com.wms.integration.adapter.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * ERP'ye gönderilecek malzeme/ürün kartı verisi.
 */
@Value
@Builder
public class MaterialDto {

    /** WMS iç SKU kodu */
    String sku;

    /** Malzeme adı */
    String name;

    /** Birim (PIECE, KG, LT, vb.) */
    String unit;

    /** Birim ağırlık */
    BigDecimal unitWeight;

    /** Barkod */
    String barcode;

    /** Kaynak lokasyon/depo ID */
    Long locationId;
}

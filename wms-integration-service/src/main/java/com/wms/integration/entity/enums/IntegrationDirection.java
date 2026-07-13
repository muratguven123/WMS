package com.wms.integration.entity.enums;

/**
 * Entegrasyon veri akış yönü.
 *
 * <ul>
 *   <li>{@code OUTBOUND} — WMS'den ERP'ye giden veri (malzeme, stok hareketi, fatura)</li>
 *   <li>{@code INBOUND}  — ERP'den WMS'e gelen veri (sipariş, kur, ürün kataloğu)</li>
 * </ul>
 */
public enum IntegrationDirection {
    INBOUND,
    OUTBOUND
}

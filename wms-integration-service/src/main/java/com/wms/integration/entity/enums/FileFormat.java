package com.wms.integration.entity.enums;

/**
 * Dosya bazlı entegrasyonlarda (SFTP vb.) kullanılacak dosya biçimi.
 *
 * <p>İş isteri 7.3 — "dosya formatı çeşitliliği (CSV/XML/JSON/Excel)".
 * {@link com.wms.integration.entity.LocationIntegrationConfig#getFileFormat()}
 * üzerinden lokasyon bazında seçilir; adaptörler payload üretirken bu
 * biçimi dikkate alır.
 *
 * <ul>
 *   <li>{@code CSV}   — Noktalı virgül/virgül ayraçlı düz metin (Logo import şablonları)</li>
 *   <li>{@code XML}   — Şemalı XML belgeleri (Logo XML import, SAP IDoc benzeri)</li>
 *   <li>{@code JSON}  — JSON belgeleri (modern ERP import job'ları)</li>
 *   <li>{@code EXCEL} — XLSX çalışma kitabı (manuel/yarı otomatik import senaryoları)</li>
 * </ul>
 */
public enum FileFormat {
    CSV,
    XML,
    JSON,
    EXCEL
}

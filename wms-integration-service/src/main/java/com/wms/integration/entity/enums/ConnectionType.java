package com.wms.integration.entity.enums;

/**
 * ERP ile bağlantı kurma yöntemi.
 *
 * <ul>
 *   <li>{@code REST}  — HTTP/HTTPS REST API (örn: SAP OData, Oracle REST)</li>
 *   <li>{@code SOAP}  — WSDL tabanlı web servis</li>
 *   <li>{@code SFTP}  — Dosya bazlı transfer (örn: Logo, Mikro)</li>
 *   <li>{@code DB}    — Doğrudan veritabanı bağlantısı (JDBC)</li>
 * </ul>
 */
public enum ConnectionType {
    REST,
    SOAP,
    SFTP,
    DB
}

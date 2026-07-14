package com.wms.integration.entity.enums;

/**
 * ERP ile bağlantı kurma yöntemi.
 *
 * <ul>
 *   <li>{@code REST}          — HTTP/HTTPS REST API (örn: SAP OData, Oracle REST)</li>
 *   <li>{@code SOAP}          — WSDL tabanlı web servis</li>
 *   <li>{@code SFTP}          — Dosya bazlı transfer (örn: Logo, Mikro);
 *                               dosya biçimi {@link FileFormat} ile belirlenir</li>
 *   <li>{@code DB}            — Doğrudan veritabanı bağlantısı (JDBC)</li>
 *   <li>{@code MESSAGE_QUEUE} — Mesaj kuyruğu üzerinden asenkron aktarım
 *                               (Kafka topic, RabbitMQ exchange, IBM MQ vb.);
 *                               kuyruk adresi {@code connectionParams} içinde tanımlanır</li>
 *   <li>{@code WEBHOOK}       — ERP/3. parti sistemin sunduğu HTTP callback adresine
 *                               HMAC-SHA256 imzalı POST (push modeli); hedef URL ve
 *                               imza anahtarı {@link com.wms.integration.entity.IntegrationSystem}
 *                               üzerindeki {@code webhookUrl}/{@code webhookSecret} alanlarından okunur,
 *                               gönderim {@link com.wms.integration.outbox.WebhookDispatcher} ile yapılır</li>
 * </ul>
 */
public enum ConnectionType {
    REST,
    SOAP,
    SFTP,
    DB,
    MESSAGE_QUEUE,
    WEBHOOK
}

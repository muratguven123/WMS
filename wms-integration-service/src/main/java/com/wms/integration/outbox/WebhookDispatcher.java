package com.wms.integration.outbox;

import com.wms.integration.adapter.dto.ErpResponse;
import com.wms.integration.entity.IntegrationSystem;
import com.wms.integration.entity.LocationIntegrationConfig;
import com.wms.integration.entity.OutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * {@link com.wms.integration.entity.enums.ConnectionType#WEBHOOK} bağlantı tipi
 * için Outbox mesajlarını HMAC-SHA256 imzalı HTTP POST olarak hedef sisteme iletir.
 *
 * <h3>İmzalama Şeması (Stripe-style)</h3>
 * <pre>
 *   signedPayload = "&lt;timestamp&gt;.&lt;rawJsonBody&gt;"
 *   signature     = hexLower( HMAC-SHA256(webhookSecret, signedPayload) )
 * </pre>
 *
 * <h3>HTTP Başlıkları</h3>
 * <ul>
 *   <li>{@code X-WMS-Signature}  — {@code v1=<hexSignature>}</li>
 *   <li>{@code X-WMS-Timestamp}  — epoch saniye (replay saldırısı koruması;
 *       alıcı ±5 dk tolerans uygulamalıdır)</li>
 *   <li>{@code X-WMS-Event}      — Outbox mesaj tipi (jobCode, örn. {@code CUSTOMER_SYNC})</li>
 *   <li>{@code X-WMS-Message-Id} — Outbox mesaj ID (alıcı tarafta idempotency anahtarı)</li>
 * </ul>
 *
 * <h3>Alıcı Tarafında Doğrulama</h3>
 * Alıcı, gövdeyi ve {@code X-WMS-Timestamp} değerini birleştirip kendi kopyasındaki
 * secret ile aynı HMAC'i hesaplar; {@link #verifySignature} referans implementasyondur.
 *
 * <p>2xx dışındaki her yanıt {@link ErpResponse#failure} olarak döner ve Outbox
 * retry mekanizmasına (exponential backoff) girer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDispatcher {

    /** İmza başlığındaki şema versiyonu — anahtar rotasyonu/algoritma değişimi için. */
    public static final String SIGNATURE_VERSION = "v1";

    public static final String HEADER_SIGNATURE  = "X-WMS-Signature";
    public static final String HEADER_TIMESTAMP  = "X-WMS-Timestamp";
    public static final String HEADER_EVENT      = "X-WMS-Event";
    public static final String HEADER_MESSAGE_ID = "X-WMS-Message-Id";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final WebClient webClient;

    /**
     * Outbox mesajını webhook hedefine imzalı POST olarak gönderir.
     *
     * @param message Outbox mesajı (payload JSON olarak gönderilir)
     * @param config  lokasyonun aktif entegrasyon konfigürasyonu
     *                (webhook alanları {@link IntegrationSystem} üzerinden okunur)
     * @return standart adaptör yanıtı — Outbox Worker retry akışıyla uyumlu
     */
    public ErpResponse dispatch(OutboxMessage message, LocationIntegrationConfig config) {
        IntegrationSystem system = config.getIntegrationSystem();

        if (system == null || system.getWebhookUrl() == null || system.getWebhookUrl().isBlank()) {
            return ErpResponse.failure("WEBHOOK_CONFIG_MISSING",
                    "webhookUrl is not configured for integration system of locationId="
                            + config.getLocationId());
        }
        if (system.getWebhookSecret() == null || system.getWebhookSecret().isBlank()) {
            return ErpResponse.failure("WEBHOOK_CONFIG_MISSING",
                    "webhookSecret is not configured for integration system '"
                            + system.getCode() + "'");
        }

        String payload   = message.getPayload();
        long   timestamp = Instant.now().getEpochSecond();
        String signature = computeSignature(system.getWebhookSecret(), timestamp, payload);

        log.info("[Webhook] Dispatching: messageId={}, event={}, url={}",
                message.getId(), message.getJobCode(), system.getWebhookUrl());

        try {
            webClient.post()
                    .uri(system.getWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HEADER_SIGNATURE, SIGNATURE_VERSION + "=" + signature)
                    .header(HEADER_TIMESTAMP, String.valueOf(timestamp))
                    .header(HEADER_EVENT, message.getJobCode())
                    .header(HEADER_MESSAGE_ID, String.valueOf(message.getId()))
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("[Webhook] Delivered: messageId={}, event={}",
                    message.getId(), message.getJobCode());
            return ErpResponse.success("WEBHOOK-" + message.getId(),
                    "Webhook delivered to " + system.getWebhookUrl());

        } catch (WebClientResponseException ex) {
            log.error("[Webhook] HTTP error: messageId={}, status={}, body={}",
                    message.getId(), ex.getStatusCode(), ex.getResponseBodyAsString());
            return ErpResponse.failure(String.valueOf(ex.getStatusCode().value()),
                    "Webhook target returned HTTP " + ex.getStatusCode()
                            + ": " + ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("[Webhook] Unexpected error: messageId={}", message.getId(), ex);
            return ErpResponse.failure("WEBHOOK_ERR", ex.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // İmza yardımcıları (statik — alıcı taraf ve testler için referans)
    // -----------------------------------------------------------------------

    /**
     * {@code timestamp + "." + payload} birleşiminin HMAC-SHA256 imzasını
     * küçük harf hex olarak üretir.
     */
    public static String computeSignature(String secret, long timestamp, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(
                    (timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 signature computation failed", ex);
        }
    }

    /**
     * Alıcı taraf doğrulaması için referans implementasyon.
     * Zamanlama saldırılarına karşı sabit süreli karşılaştırma kullanır.
     *
     * @param secret          paylaşımlı gizli anahtar
     * @param timestamp       {@code X-WMS-Timestamp} başlık değeri
     * @param payload         ham istek gövdesi
     * @param signatureHeader {@code X-WMS-Signature} başlık değeri ({@code v1=<hex>})
     * @return imza geçerliyse {@code true}
     */
    public static boolean verifySignature(String secret, long timestamp,
                                          String payload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_VERSION + "=")) {
            return false;
        }
        String received = signatureHeader.substring(SIGNATURE_VERSION.length() + 1);
        String expected = computeSignature(secret, timestamp, payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }
}

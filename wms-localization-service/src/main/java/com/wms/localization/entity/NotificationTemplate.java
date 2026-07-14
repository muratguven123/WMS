package com.wms.localization.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Çok dilli bildirim şablonu üst kaydı (İş İsteri 2.1 — madde 5.2).
 *
 * templateCode : Diğer servislerin render çağrısında kullandığı benzersiz kod.
 *                Örnek: "RECEIPT_APPROVED_MAIL", "SHIPMENT_DISPATCHED_MAIL"
 * channel      : Gönderim kanalı — EMAIL | SMS | PUSH
 *
 * Dil bazlı içerikler {@link NotificationTemplateContent} tablosunda tutulur;
 * bu kayıt dil bağımsızdır. Model, TranslationKey ↔ TranslationValue
 * ilişkisinin şablon karşılığıdır.
 *
 * Denetim: created_by / updated_by kolonlarında JWT subject saklanır
 * (country_address_template V22 deseniyle tutarlı).
 */
@Entity
@Table(
    name = "notification_template",
    indexes = {
        @Index(name = "idx_notification_template_code",    columnList = "template_code"),
        @Index(name = "idx_notification_template_channel", columnList = "channel"),
        @Index(name = "idx_notification_template_active",  columnList = "active")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * Benzersiz şablon kodu — UPPER_SNAKE_CASE önerilir.
     * Render çağrılarının sözleşme anahtarıdır; yayınlandıktan sonra
     * değiştirilmemelidir (bu yüzden update API'sinde salt-okunurdur).
     */
    @Column(name = "template_code", nullable = false, unique = true, length = 100)
    private String templateCode;

    /** Gönderim kanalı. */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    /** Geliştirici/içerik yöneticisi için açıklayıcı not. Nullable. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Pasif şablonlar render edilmez; admin preview yine de çalışır. */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Kaydı oluşturan JWT subject — denetim için. */
    @Column(name = "created_by", length = 255)
    private String createdBy;

    /** Son değişikliği yapan JWT subject — denetim için. */
    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

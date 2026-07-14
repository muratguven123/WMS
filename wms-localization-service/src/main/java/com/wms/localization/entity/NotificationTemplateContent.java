package com.wms.localization.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bir bildirim şablonunun belirli bir dildeki içeriği.
 *
 * Benzersizlik kuralı: (template_id, language_code) çifti tekrar edemez.
 * Hem DB unique constraint hem JPA UniqueConstraint ile korunur
 * (TranslationValue ile aynı yaklaşım).
 *
 * languageCode : {@link Language#getCode()} ile eşleşen ISO 639-1 kod.
 *                FK yerine kod saklanır (MissingTranslationLog.locale deseni) —
 *                dil pasifleştirilse bile içerik yetim FK hatası üretmez.
 * subject      : EMAIL/PUSH başlığı; SMS'te null bırakılır. Placeholder içerebilir.
 * body         : Şablon gövdesi — {{variable}} placeholder söz dizimi.
 *                Örnek: "Sayın {{userName}}, {{receiptNumber}} numaralı kabul onaylandı."
 */
@Entity
@Table(
    name = "notification_template_content",
    uniqueConstraints = {
        @UniqueConstraint(
            name  = "uq_ntc_template_language",
            columnNames = {"template_id", "language_code"}
        )
    },
    indexes = {
        @Index(name = "idx_ntc_template_id",   columnList = "template_id"),
        @Index(name = "idx_ntc_language_code", columnList = "language_code")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplateContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * Hangi şablona ait olduğu.
     * LAZY: İçerik yüklenirken şablon metadata'sı her zaman gerekmez.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_ntc_template"))
    private NotificationTemplate template;

    /** ISO 639-1 dil kodu — küçük harf normalize edilerek saklanır. */
    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    /** Başlık — EMAIL için zorunlu (uygulama katmanında enforce edilir). */
    @Column(name = "subject", length = 500)
    private String subject;

    /** Şablon gövdesi — {{variable}} placeholder'ları içerir. */
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Son değişikliği yapan JWT subject — denetim için. */
    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    @PreUpdate
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}

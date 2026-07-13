package com.wms.localization.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bir anahtarın belirli bir dildeki çeviri değerini tutar.
 *
 * Benzersizlik kuralı: (language_id, translation_key_id) çifti tekrar edemez.
 * Bu kural hem DB unique constraint hem de JPA UniqueConstraint ile korunur.
 */
@Entity
@Table(
    name = "translation_value",
    uniqueConstraints = {
        @UniqueConstraint(
            name  = "uq_translation_value_lang_key",
            columnNames = {"language_id", "translation_key_id"}
        )
    },
    indexes = {
        @Index(name = "idx_translation_value_language_id",       columnList = "language_id"),
        @Index(name = "idx_translation_value_translation_key_id", columnList = "translation_key_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranslationValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * Hangi dile ait olduğu.
     * LAZY: Translation değeri yüklenirken dil her zaman gerekmez.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "language_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_translation_value_language"))
    private Language language;

    /**
     * Hangi anahtara karşılık geldiği.
     * LAZY: Anahtar metadata'sı her zaman gerekmez.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "translation_key_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_translation_value_key"))
    private TranslationKey translationKey;

    /**
     * Çevrilmiş metin.
     * TEXT tipi: Uzun rapor başlıkları veya e-posta şablonları için yeterli alan.
     */
    @Column(name = "value", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}

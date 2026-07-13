package com.wms.localization.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Çevrilecek metin anahtarlarını (key) tutar.
 *
 * keyCode  : Nokta-notasyonlu hiyerarşik anahtar. Örnek:
 *            "common.buttons.save", "errors.stock.not_found", "report.header.title"
 * module   : Anahtarın ait olduğu modül. Enum benzeri sabit değerler:
 *            UI | REPORT | EMAIL | SYSTEM
 *
 * Performans: keyCode ve module sütunları sık sorgulanır → ayrı index'ler.
 */
@Entity
@Table(
    name = "translation_key",
    indexes = {
        @Index(name = "idx_translation_key_code",   columnList = "key_code"),
        @Index(name = "idx_translation_key_module", columnList = "module")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranslationKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * Benzersiz metin anahtarı kodu.
     * Max 255 karakter; nokta ile ayrılmış namespace önerilir.
     */
    @Column(name = "key_code", nullable = false, unique = true, length = 255)
    private String keyCode;

    /**
     * Anahtarın kullanıldığı modül.
     * Değerler: UI, REPORT, EMAIL, SYSTEM
     * String olarak tutulur — yeni modül eklemek için migration gerekmez.
     */
    @Column(name = "module", nullable = false, length = 50)
    private String module;

    /** Geliştirici/çevirmen için açıklayıcı not. Nullable. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

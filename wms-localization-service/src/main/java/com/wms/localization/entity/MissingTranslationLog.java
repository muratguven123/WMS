package com.wms.localization.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Sistemde karşılığı bulunamayan çeviri anahtarlarını loglar.
 *
 * Amaç: Admin panelinde "Eksik Çeviriler" raporu üretmek.
 * Kayıtlar async yazılır — ana request akışını engellemez.
 *
 * firstSeenAt : Anahtarın ilk kez eksik görüldüğü zaman
 * lastSeenAt  : En son ne zaman eksik olduğu (hit count ile birlikte izlenir)
 * hitCount    : Bu anahtarın kaç kez eksik bulunduğu — önceliklendirme için
 * resolved    : Çeviri tamamlandıktan sonra admin tarafından işaretlenir
 */
@Entity
@Table(
    name = "missing_translation_log",
    indexes = {
        @Index(name = "idx_mtl_key_code",   columnList = "key_code"),
        @Index(name = "idx_mtl_locale",     columnList = "locale"),
        @Index(name = "idx_mtl_resolved",   columnList = "resolved"),
        @Index(name = "idx_mtl_last_seen",  columnList = "last_seen_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name       = "uq_mtl_locale_key",
            columnNames = {"locale", "key_code"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissingTranslationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /** Hangi dil için eksik */
    @Column(name = "locale", nullable = false, length = 10)
    private String locale;

    /** Hangi anahtar eksik */
    @Column(name = "key_code", nullable = false, length = 255)
    private String keyCode;

    /** Anahtarın modülü — hızlı filtreleme için denormalize */
    @Column(name = "module", length = 50)
    private String module;

    /** İlk kez ne zaman eksik bulundu */
    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    /** En son ne zaman eksik bulundu */
    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    /**
     * Kaç kez eksik bulundu.
     * Yüksek hit count → yüksek öncelikli çeviri ihtiyacı.
     */
    @Column(name = "hit_count", nullable = false)
    @Builder.Default
    private long hitCount = 1L;

    /**
     * Çevirisi tamamlandı mı?
     * Admin panelinden manuel olarak işaretlenir.
     */
    @Column(name = "resolved", nullable = false)
    @Builder.Default
    private boolean resolved = false;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}

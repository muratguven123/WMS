package com.wms.localization.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * İstenen dilde içeriği bulunamayan bildirim şablonlarını loglar
 * (MissingTranslationLog'un şablon karşılığı).
 *
 * Amaç: Admin panelinde "Eksik Şablon İçerikleri" raporu üretmek.
 * Kayıtlar async yazılır — render akışını engellemez.
 *
 * Not: Fallback başarılı olsa bile (varsayılan dile düşüldüyse) istenen dil
 * eksik olduğu için kayıt atılır; içerik tamamlanınca resolved işaretlenir.
 */
@Entity
@Table(
    name = "missing_template_log",
    indexes = {
        @Index(name = "idx_mtpl_template_code", columnList = "template_code"),
        @Index(name = "idx_mtpl_locale",        columnList = "locale"),
        @Index(name = "idx_mtpl_resolved",      columnList = "resolved"),
        @Index(name = "idx_mtpl_last_seen",     columnList = "last_seen_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name       = "uq_mtpl_locale_template",
            columnNames = {"locale", "template_code"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissingTemplateLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /** Hangi dil için eksik */
    @Column(name = "locale", nullable = false, length = 10)
    private String locale;

    /** Hangi şablon eksik */
    @Column(name = "template_code", nullable = false, length = 100)
    private String templateCode;

    /** Şablonun kanalı — hızlı filtreleme için denormalize */
    @Column(name = "channel", length = 20)
    private String channel;

    /** İlk kez ne zaman eksik bulundu */
    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    /** En son ne zaman eksik bulundu */
    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    /**
     * Kaç kez eksik bulundu.
     * Yüksek hit count → yüksek öncelikli içerik ihtiyacı.
     */
    @Column(name = "hit_count", nullable = false)
    @Builder.Default
    private long hitCount = 1L;

    /**
     * İçerik tamamlandı mı?
     * İçerik upsert edildiğinde otomatik işaretlenir.
     */
    @Column(name = "resolved", nullable = false)
    @Builder.Default
    private boolean resolved = false;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}

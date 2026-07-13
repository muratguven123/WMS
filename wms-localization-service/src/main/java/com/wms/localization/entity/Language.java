package com.wms.localization.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Sistemde desteklenen dilleri tutar.
 * code: ISO 639-1 formatında (tr, en, de, fr …)
 * isDefault: Yalnızca bir kayıt true olabilir (uygulama katmanında enforce edilir).
 */
@Entity
@Table(
    name = "language",
    indexes = {
        @Index(name = "idx_language_code", columnList = "code"),
        @Index(name = "idx_language_is_active", columnList = "is_active")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Language {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * ISO 639-1 dil kodu — zorunlu, benzersiz.
     * Örnek: "tr", "en", "de"
     */
    @Column(name = "code", nullable = false, unique = true, length = 10)
    private String code;

    /** İnsan tarafından okunabilir dil adı. Örnek: "Türkçe", "English" */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Varsayılan dil bayrağı.
     * Yalnızca tek satır true olabilir; uygulama servisi bu bütünlüğü yönetir.
     */
    @Column(name = "is_default", nullable = false)
    @JsonProperty("isDefault")
    private boolean isDefault;

    @Column(name = "is_active", nullable = false)
    @JsonProperty("isActive")
    private boolean isActive;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}

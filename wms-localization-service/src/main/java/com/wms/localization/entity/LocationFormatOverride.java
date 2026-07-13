package com.wms.localization.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Depo (Location) bazlı format geçersiz kılma kuralları.
 * <p>
 * Bu tablo, belirli bir deponun ülke varsayılanından farklı bir tarih/saat/sayı
 * formatı kullanması gerektiğinde doldurulur. Kayıt yoksa {@link CountryFormatConfig}
 * devreye girer — bu hiyerarşi servis katmanında uygulanır.
 * </p>
 */
@Entity
@Table(
    name = "location_format_override",
    indexes = {
        @Index(name = "idx_lfo_location_id", columnList = "location_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationFormatOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * wms-core-service'teki Location (depo) entity'sine dış referans.
     * Unique kısıt: her depo için en fazla bir geçersiz kılma kaydı olabilir.
     */
    @Column(name = "location_id", nullable = false, unique = true)
    private Long locationId;

    /**
     * Tarih format deseni. Null ise ülke varsayılanı kullanılır.
     * Örnek: "dd.MM.yyyy", "MM/dd/yyyy"
     */
    @Column(name = "date_format", length = 20)
    private String dateFormat;

    /**
     * Saat format deseni. Null ise ülke varsayılanı kullanılır.
     * Örnek: "HH:mm", "hh:mm a"
     */
    @Column(name = "time_format", length = 20)
    private String timeFormat;

    /**
     * Ondalık ayraç karakteri. Null ise ülke varsayılanı kullanılır.
     */
    @Column(name = "decimal_separator", length = 1)
    private String decimalSeparator;

    /**
     * Binlik ayraç karakteri. Null ise ülke varsayılanı kullanılır.
     */
    @Column(name = "thousand_separator", length = 1)
    private String thousandSeparator;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}

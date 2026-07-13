package com.wms.localization.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Ülke bazlı tarih, saat ve sayısal biçimlendirme kurallarını saklar.
 * <p>
 * Bir deponun (Location) kendi {@link LocationFormatOverride} kaydı yoksa,
 * bağlı olduğu ülkenin bu yapılandırması varsayılan olarak kullanılır.
 * </p>
 *
 * <ul>
 *   <li>dateFormat  — "dd.MM.yyyy" | "MM/dd/yyyy" | "yyyy-MM-dd" vb.</li>
 *   <li>timeFormat  — "HH:mm" | "hh:mm a" vb.</li>
 *   <li>decimalSeparator   — "," veya "."</li>
 *   <li>thousandSeparator  — "." veya ","</li>
 * </ul>
 */
@Entity
@Table(
    name = "country_format_config",
    indexes = {
        @Index(name = "idx_cfc_country_id", columnList = "country_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryFormatConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * wms-core-service'teki Country entity'sine dış referans.
     * Unique kısıt: her ülke için yalnızca tek bir format config bulunabilir.
     */
    @Column(name = "country_id", nullable = false, unique = true)
    private Long countryId;

    /**
     * Tarih format deseni.
     * Örnek: "dd.MM.yyyy", "MM/dd/yyyy", "yyyy-MM-dd"
     */
    @Column(name = "date_format", nullable = false, length = 20)
    private String dateFormat;

    /**
     * Saat format deseni.
     * Örnek: "HH:mm", "HH:mm:ss", "hh:mm a"
     */
    @Column(name = "time_format", nullable = false, length = 20)
    private String timeFormat;

    /**
     * Ondalık ayraç karakteri.
     * Örnek: "," (Avrupa) veya "." (ABD/İngiltere)
     */
    @Column(name = "decimal_separator", nullable = false, length = 1)
    private String decimalSeparator;

    /**
     * Binlik ayraç karakteri.
     * Örnek: "." (Avrupa) veya "," (ABD/İngiltere)
     */
    @Column(name = "thousand_separator", nullable = false, length = 1)
    private String thousandSeparator;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}

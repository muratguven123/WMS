package com.wms.localization.domain.address;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;


/**
 * Ülkeye özgü adres alan şablonu.
 * Hangi ülkede hangi alanların zorunlu olduğunu,
 * sırasını ve validasyon kuralını tanımlar.
 */
@Entity
@Table(
    name = "country_address_template",
    schema = "localization",
    indexes = {
        @Index(name = "idx_cat_country_id", columnList = "country_id"),
        @Index(name = "idx_cat_country_sequence", columnList = "country_id, sequence")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryAddressTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * Referans verilen ülke (wms-core-service Country entity'si Long'si).
     * Cross-service FK — DB kısıtı yoktur, uygulama seviyesinde yönetilir.
     */
    @Column(name = "country_id", nullable = false)
    private Long countryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "address_template_field_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_cat_template_field")
    )
    private AddressTemplateField addressTemplateField;

    /** Bu alan söz konusu ülke için zorunlu mu? */
    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory;

    /** Formda gösterilme sırası. */
    @Column(name = "sequence", nullable = false)
    private int sequence;

    /**
     * Alana uygulanacak validasyon regex'i (opsiyonel).
     * Örn: TR zip kodu için "^[0-9]{5}$"
     */
    @Column(name = "validation_regex", length = 500)
    private String validationRegex;

    /**
     * Validasyon hatası için i18n mesaj anahtarı (opsiyonel).
     * Örn: "validation.zipCode.invalid"
     */
    @Column(name = "error_message_key", length = 255)
    private String errorMessageKey;

    /** Son değişiklik zamanı (İş İsteri 17 denetim). */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** JWT subject — son değişikliği yapan kullanıcı. */
    @Column(name = "updated_by", length = 255)
    private String updatedBy;
}

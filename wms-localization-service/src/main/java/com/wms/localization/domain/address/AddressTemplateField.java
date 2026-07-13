package com.wms.localization.domain.address;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

/**
 * Ülkeden bağımsız adres alan şablonu.
 * fieldKey örnekleri: "district", "neighborhood", "door_no", "apartment_no"
 */
@Entity
@Table(
    name = "address_template_field",
    schema = "localization",
    uniqueConstraints = @UniqueConstraint(name = "uq_address_template_field_key", columnNames = "field_key")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressTemplateField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * Benzersiz alan anahtarı (örn: "district", "neighborhood", "door_no").
     * JSONB içindeki Map key'i ile eşleşir.
     */
    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    /**
     * i18n mesaj anahtarı (örn: "fields.district", "fields.door_no").
     */
    @Column(name = "field_label_key", nullable = false, length = 255)
    private String fieldLabelKey;

    /**
     * Alanın giriş tipi: serbest metin mi, yoksa core-service master-data
     * cascade'ine bağlı bir seçim kutusu mu.
     *
     * <p>Bu enum sabittir ama içine girecek "hangi ülke hangi tipte" bilgisi
     * sabit değildir — bu, {@link CountryAddressTemplate} ile birlikte
     * tamamen veritabanından okunur.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 20)
    @Builder.Default
    private FieldType fieldType = FieldType.TEXT;

    /**
     * {@code fieldType == MASTER_SELECT} ise, core-service'in hangi cascade
     * endpoint'inden (state/city/district/neighborhood) veri çekileceğini belirtir.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "master_data_source", nullable = false, length = 20)
    @Builder.Default
    private MasterDataSource masterDataSource = MasterDataSource.NONE;

    /**
     * Bu alanın seçili değerinin bağımlı olduğu üst alan.
     * Ülke seçimi için {@code __country__}; template içi bağımlılık için
     * ilgili alanın {@code fieldKey}'i (örn. district → "city").
     */
    @Column(name = "parent_field_key", length = 100)
    private String parentFieldKey;

    @OneToMany(mappedBy = "addressTemplateField", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CountryAddressTemplate> countryTemplates;

    public enum FieldType {
        TEXT,
        MASTER_SELECT,
        /**
         * Alan zaten sabit country→state→city cascade'i tarafından render
         * ediliyor (city/state); bu tip sadece mandatory/regex kuralını
         * template üzerinden taşımak için kullanılır, UI tarafından ayrıca
         * render edilmez.
         */
        FIXED
    }

    public enum MasterDataSource {
        NONE,
        /** Eyalet / bölge — core /api/address/states */
        STATE,
        /** İl / şehir — core /api/address/cities */
        CITY,
        DISTRICT,
        NEIGHBORHOOD
    }
}

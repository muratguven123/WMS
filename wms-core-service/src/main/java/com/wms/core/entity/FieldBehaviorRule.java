package com.wms.core.entity;

import com.wms.core.entity.enums.FieldBehavior;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Bir form alanının belirli bir bağlamda nasıl davranacağını tanımlayan kural.
 *
 * <h3>Kural Eşleşme Mantığı</h3>
 * <p>Nullable olan tüm bağlam alanları ({@code companyId}, {@code countryId},
 * {@code locationId}, {@code roleId}, {@code operationType}) "herkes için geçerli"
 * anlamına gelir. Kural motoru ({@code DynamicUiService}) gelen {@code UiContext}
 * ile kısmi veya tam eşleşen kuralları filtreler.</p>
 *
 * <h3>Öncelik Hiyerarşisi (varsayılan atama)</h3>
 * <pre>
 *   Lokasyon = 50 | Rol = 40 | Şirket = 30 | Ülke = 20 | Varsayılan = 10
 * </pre>
 *
 * <p>Aynı alan için birden fazla kural eşleşirse en yüksek {@code priority} değerine
 * sahip kural kazanır.</p>
 *
 * <p>Bu entity {@link BaseEntity}'yi <b>extend etmez</b>: soft-delete ve
 * {@code createdAt} yönetimine ihtiyaç duyulmaz; kural silindiğinde gerçekten
 * veritabanından kaldırılır (hard-delete). Değişiklik geçmişi
 * {@link ConfigurationAuditLog} üzerinden takip edilir.</p>
 */
@Entity
@Table(
        name = "field_behavior_rules",
        indexes = {
                @Index(name = "idx_fbr_screen_field", columnList = "screen_field_id"),
                @Index(name = "idx_fbr_location",     columnList = "location_id"),
                @Index(name = "idx_fbr_role",         columnList = "role_id"),
                @Index(name = "idx_fbr_company",      columnList = "company_id"),
                @Index(name = "idx_fbr_country",      columnList = "country_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldBehaviorRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /** Bu kuralın uygulandığı ekran alanı. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "screen_field_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_fbr_screen_field")
    )
    private ScreenField screenField;

    /**
     * Kural önceliği. Çakışan kurallarda büyük değer kazanır.
     * Varsayılan hiyerarşi: Lokasyon=50, Rol=40, Şirket=30, Ülke=20, Global=10.
     */
    @Column(name = "priority", nullable = false)
    private int priority;

    // ── Bağlam Filtreleri (nullable = "hepsi için geçerli") ────────────────

    /** Hangi şirket için geçerli. Null → tüm şirketler. */
    @Column(name = "company_id")
    private Long companyId;

    /** Hangi ülke için geçerli. Null → tüm ülkeler. */
    @Column(name = "country_id")
    private Long countryId;

    /** Hangi lokasyon için geçerli. Null → tüm lokasyonlar. */
    @Column(name = "location_id")
    private Long locationId;

    /** Hangi rol için geçerli. Null → tüm roller. */
    @Column(name = "role_id")
    private Long roleId;

    /**
     * Hangi operasyon tipi için geçerli — örn: {@code CREATE}, {@code EDIT}, {@code VIEW}.
     * Null → tüm operasyonlar.
     */
    @Column(name = "operation_type", length = 50)
    private String operationType;

    // ── Kural Çıktısı ──────────────────────────────────────────────────────

    /** Bu bağlamda alanın alacağı davranış. */
    @Enumerated(EnumType.STRING)
    @Column(name = "behavior", nullable = false, length = 20)
    private FieldBehavior behavior;

    /**
     * Alanı önceden dolu göstermek için varsayılan değer.
     * Null → varsayılan değer uygulanmaz.
     */
    @Column(name = "default_value", length = 500)
    private String defaultValue;

    /**
     * Sunucu tarafı validasyon regex deseni.
     * Null → regex kontrolü yapılmaz.
     * Örn: {@code ^\d{10}$} (10 haneli vergi numarası)
     */
    @Column(name = "validation_regex", length = 500)
    private String validationRegex;

    /**
     * Regex başarısız olduğunda kullanılacak çeviri anahtarı.
     * Örn: {@code validation.tax_number.invalid}
     */
    @Column(name = "validation_error_message_key", length = 200)
    private String validationErrorMessageKey;

    /** Kuralın son güncellenme zamanı. */
    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;
}

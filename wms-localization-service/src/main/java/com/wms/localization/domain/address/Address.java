package com.wms.localization.domain.address;

import com.wms.localization.listener.AddressEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Dinamik adres kaydı.
 *
 * <p>Sabit alanlar (city, state, zipCode) doğrudan sütun olarak tutulur ve
 * indekslenir. Ülkeye özgü dinamik alanlar (district, neighborhood, door_no
 * vb.) {@code addressDetails} JSONB sütununda {@code Map<String, Object>}
 * olarak saklanır; key'ler {@link AddressTemplateField#getFieldKey()} ile
 * eşleşir.</p>
 *
 * <p>Hibernate 6 JSONB desteği için {@code @JdbcTypeCode(SqlTypes.JSON)}
 * kullanılır; Hibernate 5'teki custom UserType gereksinimi ortadan kalkar.</p>
 */
@Entity
@EntityListeners(AddressEntityListener.class)
@Table(
    name = "address",
    schema = "localization",
    indexes = {
        @Index(name = "idx_address_country_id", columnList = "country_id"),
        @Index(name = "idx_address_city",       columnList = "city"),
        @Index(name = "idx_address_state",      columnList = "state"),
        @Index(name = "idx_address_zip_code",   columnList = "zip_code")
        // GIN index addressDetails üzerinde DDL migration'da oluşturulur;
        // JPA @Index ile gin tipi desteklenmediği için SQL el ile yazılır.
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * İlgili ülke Long'si (cross-service referans).
     * Şablonun hangi ülkeye ait olduğunu belirtir.
     */
    @Column(name = "country_id", nullable = false)
    private Long countryId;

    @Column(name = "city", length = 150)
    private String city;

    @Column(name = "state", length = 150)
    private String state;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    /**
     * Ülkeye özgü dinamik adres alanları.
     *
     * <ul>
     *   <li>Key  → {@link AddressTemplateField#getFieldKey()} (örn: "district")</li>
     *   <li>Value → kullanıcının girdiği değer (String, Integer vb.)</li>
     * </ul>
     *
     * <p>PostgreSQL sütun tipi: {@code jsonb}. Hibernate 6, {@code @JdbcTypeCode(SqlTypes.JSON)}
     * ile otomatik serileştirme/deserileştirme yapar (Jackson üzerinden).</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "address_details", columnDefinition = "jsonb")
    private Map<String, Object> addressDetails;

    /**
     * İnsan tarafından okunabilir biçimlendirilmiş adres.
     * Örn: "Kadıköy, Moda Mah., Bahariye Cad. No:5 D:3, 34710 İstanbul, TR"
     */
    @Column(name = "formatted_address", columnDefinition = "text")
    private String formattedAddress;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

package com.wms.core.entity;

import com.wms.core.entity.enums.StorageLocationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * Depo içindeki en küçük fiziksel saklama birimidir: raf gözü (bin).
 *
 * <p>Adres hiyerarşisi: {@code Koridor (aisle) → Bölüm (bay) → Kat (shelf) → Göz (bin)}</p>
 * <p>Örnek adres kodu: {@code A-01-03-01} = A Koridoru, 1. Bölüm, 3. Kat, 1. Göz</p>
 *
 * <p>{@code addressCode} alanı {@link #buildAddressCode()} metodu aracılığıyla
 * persist ve update öncesinde otomatik olarak hesaplanır; manuel set edilmemelidir.</p>
 *
 * <p>Kapasite durumu servisten yönetilir:
 * {@code currentVolume} veya {@code currentWeight} max değere ulaştığında
 * {@code status} otomatik olarak {@code FULL} yapılır.</p>
 */
@Entity
@Table(
    name = "storage_locations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_sl_address_code",
            columnNames = {"address_code"}
        ),
        @UniqueConstraint(
            name = "uk_sl_zone_aisle_bay_shelf_bin",
            columnNames = {"zone_id", "aisle", "bay", "shelf", "bin"}
        )
    }
)
@SQLDelete(sql = "UPDATE storage_locations SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageLocation extends BaseEntity {

    // -----------------------------------------------------------------------
    // İlişkiler
    // -----------------------------------------------------------------------

    /**
     * Bu raf gözünün ait olduğu zone.
     * LAZY yükleme: performans açısından zorunlu.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "zone_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_sl_zone")
    )
    private Zone zone;

    // -----------------------------------------------------------------------
    // Adres Hiyerarşisi
    // -----------------------------------------------------------------------

    /** Koridor kodu. Örn: A, B, C */
    @Column(name = "aisle", nullable = false, length = 10)
    private String aisle;

    /** Bölüm kodu. Örn: 01, 02, 12 */
    @Column(name = "bay", nullable = false, length = 10)
    private String bay;

    /** Kat / raf kodu. Örn: 01, 02, 03 */
    @Column(name = "shelf", nullable = false, length = 10)
    private String shelf;

    /** Göz kodu. Örn: 01, 02 */
    @Column(name = "bin", nullable = false, length = 10)
    private String bin;

    /**
     * Otomatik üretilen adres kodu.
     * Format: {@code [aisle]-[bay]-[shelf]-[bin]} → Örn: {@code A-01-03-01}
     * Bu alan {@link #buildAddressCode()} tarafından persist/update öncesinde doldurulur.
     * Dışarıdan set edilmemelidir.
     */
    @Column(name = "address_code", nullable = false, length = 50, updatable = true)
    private String addressCode;

    // -----------------------------------------------------------------------
    // Kapasite Alanları
    // -----------------------------------------------------------------------

    /**
     * Bu gözün maksimum hacim kapasitesi (m³).
     * Precision 10, scale 4: 999999.9999 m³'e kadar destekler.
     */
    @Column(name = "max_volume", nullable = false, precision = 10, scale = 4)
    private BigDecimal maxVolume;

    /**
     * Bu gözün maksimum ağırlık kapasitesi (kg).
     */
    @Column(name = "max_weight", nullable = false, precision = 10, scale = 4)
    private BigDecimal maxWeight;

    /**
     * Gözdeki mevcut stokların toplam hacmi (m³).
     * LocationCapacityService tarafından güncellenir.
     */
    @Builder.Default
    @Column(name = "current_volume", nullable = false, precision = 10, scale = 4)
    private BigDecimal currentVolume = BigDecimal.ZERO;

    /**
     * Gözdeki mevcut stokların toplam ağırlığı (kg).
     * LocationCapacityService tarafından güncellenir.
     */
    @Builder.Default
    @Column(name = "current_weight", nullable = false, precision = 10, scale = 4)
    private BigDecimal currentWeight = BigDecimal.ZERO;

    // -----------------------------------------------------------------------
    // Durum
    // -----------------------------------------------------------------------

    /**
     * Göz operasyonel durumu.
     * ACTIVE: kullanılabilir | BLOCKED: manuel kapalı | FULL: kapasite dolu
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StorageLocationStatus status = StorageLocationStatus.ACTIVE;

    // -----------------------------------------------------------------------
    // JPA Lifecycle Hooks — Adres Kodu Otomatik Üretimi
    // -----------------------------------------------------------------------

    /**
     * Kayıt oluşturulmadan veya güncellenmeden önce {@code addressCode} alanını
     * {@code [aisle]-[bay]-[shelf]-[bin]} formatında otomatik olarak doldurur.
     *
     * <p>Bu sayede uygulama kodunun her yerinde manuel format oluşturmaya gerek kalmaz
     * ve adres kodunun tutarlılığı garanti altına alınır.</p>
     */
    @PrePersist
    @PreUpdate
    private void buildAddressCode() {
        this.addressCode = String.join("-", aisle, bay, shelf, bin);
    }
}

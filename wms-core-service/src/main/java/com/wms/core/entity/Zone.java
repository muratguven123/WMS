package com.wms.core.entity;

import com.wms.core.entity.enums.ZoneType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/**
 * Depo içindeki fiziksel alanı (Zone) temsil eder.
 * Örn: COLD_ZONE (Soğuk Oda), QUARANTINE (Karantina), STAGING vb.
 *
 * <p>Her Zone, bir Location'a (Depo) aittir. Aynı depoda aynı code veya
 * name'den yalnızca bir Zone bulunabilir (bkz. UniqueConstraint).</p>
 */
@Entity
@Table(
    name = "zones",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_zone_location_name", columnNames = {"location_id", "name"}),
        @UniqueConstraint(name = "uk_zone_location_code", columnNames = {"location_id", "code"})
    }
)
@SQLDelete(sql = "UPDATE zones SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Zone extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_zone_location"))
    private Location location;

    /**
     * Makine tarafından kullanılan benzersiz kod.
     * Örn: COLD_ZONE, QUARANTINE, STAGING_A
     */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    /**
     * İnsan tarafından okunabilir görünen isim.
     * Örn: "Soğuk Depolama Alanı"
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Zone'un amacını açıklayan opsiyonel açıklama metni.
     */
    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ZoneType type;

    /**
     * Bu zone'a ait raf gözlerinin listesi.
     */
    @OneToMany(mappedBy = "zone", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StorageLocation> storageLocations = new ArrayList<>();
}

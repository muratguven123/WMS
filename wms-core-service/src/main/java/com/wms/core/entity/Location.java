package com.wms.core.entity;

import com.wms.core.entity.enums.LocationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "locations")
@SQLDelete(sql = "UPDATE locations SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_location_company"))
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_location_region"))
    private Region region;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private LocationType type;

    /**
     * IANA timezone formatı — örn: Europe/Istanbul, America/New_York.
     * Prompt 1.2'de zaman dönüşüm servisi bu alana dayanacak.
     */
    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    @OneToMany(mappedBy = "location", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Zone> zones = new ArrayList<>();

    @OneToMany(mappedBy = "location", fetch = FetchType.LAZY)
    @Builder.Default
    private List<UserAccess> userAccesses = new ArrayList<>();
}

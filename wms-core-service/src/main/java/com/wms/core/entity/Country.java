package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "countries",
    uniqueConstraints = @UniqueConstraint(name = "uk_country_iso_code", columnNames = "iso_code"),
    indexes = {
        @Index(name = "idx_country_name",      columnList = "name"),
        @Index(name = "idx_country_is_active", columnList = "is_active")
    }
)
@SQLDelete(sql = "UPDATE countries SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Country extends BaseEntity {

    @Column(name = "iso_code", nullable = false, length = 3)
    private String isoCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @OneToMany(mappedBy = "country", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Region> regions = new ArrayList<>();

    @OneToMany(mappedBy = "country", fetch = FetchType.LAZY)
    @Builder.Default
    private List<StateProvince> stateProvinces = new ArrayList<>();

    @OneToMany(mappedBy = "country", fetch = FetchType.LAZY)
    @Builder.Default
    private List<City> cities = new ArrayList<>();
}

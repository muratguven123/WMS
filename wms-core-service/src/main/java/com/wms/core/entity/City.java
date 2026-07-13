package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "cities",
    indexes = {
        @Index(name = "idx_city_country",    columnList = "country_id"),
        @Index(name = "idx_city_state",      columnList = "state_province_id"),
        @Index(name = "idx_city_name",       columnList = "name"),
        @Index(name = "idx_city_is_active",  columnList = "is_active")
    }
)
@SQLDelete(sql = "UPDATE cities SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class City extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_city_country"))
    private Country country;

    /**
     * Nullable — eyalet yapısı olmayan ülkelerde null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_province_id",
                foreignKey = @ForeignKey(name = "fk_city_state_province"))
    private StateProvince stateProvince;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @OneToMany(mappedBy = "city", fetch = FetchType.LAZY)
    @Builder.Default
    private List<District> districts = new ArrayList<>();
}

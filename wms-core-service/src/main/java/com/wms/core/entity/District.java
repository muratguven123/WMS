package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "districts",
    indexes = {
        @Index(name = "idx_district_city",      columnList = "city_id"),
        @Index(name = "idx_district_name",      columnList = "name"),
        @Index(name = "idx_district_is_active", columnList = "is_active")
    }
)
@SQLDelete(sql = "UPDATE districts SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class District extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_district_city"))
    private City city;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @OneToMany(mappedBy = "district", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Neighborhood> neighborhoods = new ArrayList<>();
}

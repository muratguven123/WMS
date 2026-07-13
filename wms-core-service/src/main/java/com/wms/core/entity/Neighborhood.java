package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
    name = "neighborhoods",
    indexes = {
        @Index(name = "idx_neighborhood_district",  columnList = "district_id"),
        @Index(name = "idx_neighborhood_name",      columnList = "name"),
        @Index(name = "idx_neighborhood_zip_code",  columnList = "zip_code"),
        @Index(name = "idx_neighborhood_is_active", columnList = "is_active")
    }
)
@SQLDelete(sql = "UPDATE neighborhoods SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Neighborhood extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "district_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_neighborhood_district"))
    private District district;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "zip_code", length = 20)
    private String zipCode;
}

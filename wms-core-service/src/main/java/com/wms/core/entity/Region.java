package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/**
 * Coğrafi bölge — Location (depo) ile Region arasındaki ilişki için.
 * Örn: Marmara, Bayern, California.
 */
@Entity
@Table(name = "regions",
       uniqueConstraints = @UniqueConstraint(name = "uk_region_country_name", columnNames = {"country_id", "name"}))
@SQLDelete(sql = "UPDATE regions SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Region extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_region_country"))
    private Country country;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @OneToMany(mappedBy = "region", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Location> locations = new ArrayList<>();
}

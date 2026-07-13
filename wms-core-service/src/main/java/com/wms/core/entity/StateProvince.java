package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/**
 * Eyalet / İl / Bölge — ülkeye bağlı birinci seviye idari birim.
 * Eyalet yapısı olmayan ülkelerde kullanılmaz; City doğrudan Country'ye bağlanır.
 */
@Entity
@Table(
    name = "state_provinces",
    uniqueConstraints = @UniqueConstraint(name = "uk_state_country_code", columnNames = {"country_id", "code"}),
    indexes = {
        @Index(name = "idx_state_country",    columnList = "country_id"),
        @Index(name = "idx_state_name",       columnList = "name"),
        @Index(name = "idx_state_is_active",  columnList = "is_active")
    }
)
@SQLDelete(sql = "UPDATE state_provinces SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StateProvince extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_state_province_country"))
    private Country country;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", length = 10)
    private String code;

    @OneToMany(mappedBy = "stateProvince", fetch = FetchType.LAZY)
    @Builder.Default
    private List<City> cities = new ArrayList<>();
}

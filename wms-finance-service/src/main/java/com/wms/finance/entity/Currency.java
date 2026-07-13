package com.wms.finance.entity;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "currencies", schema = "finance",
        uniqueConstraints = @UniqueConstraint(name = "uk_currencies_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Currency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "code", nullable = false, length = 3, unique = true)
    private String code;

    @Column(name = "symbol", nullable = false, length = 10)
    private String symbol;

    @Column(name = "decimal_places", nullable = false)
    private int decimalPlaces;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}

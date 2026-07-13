package com.wms.finance.entity;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "tax_types", schema = "finance",
        uniqueConstraints = @UniqueConstraint(name = "uq_tax_type_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "code", nullable = false, length = 20, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}

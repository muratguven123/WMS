package com.wms.finance.entity;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "system_config", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "default_currency_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_system_config_currency"))
    private Currency defaultCurrency;

    @Column(name = "strict_customer_rate", nullable = false)
    private boolean strictCustomerRate = false;
}

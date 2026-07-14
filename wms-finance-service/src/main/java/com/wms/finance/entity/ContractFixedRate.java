package com.wms.finance.entity;

import com.wms.finance.entity.enums.RateType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "contract_fixed_rates", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractFixedRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cfr_contract"))
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_currency_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cfr_source_currency"))
    private Currency sourceCurrency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_currency_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cfr_target_currency"))
    private Currency targetCurrency;

    @Column(name = "rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal rate;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 30)
    private RateType rateType;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}

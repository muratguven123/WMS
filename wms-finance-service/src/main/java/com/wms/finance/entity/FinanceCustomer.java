package com.wms.finance.entity;

import com.wms.finance.entity.enums.ExchangeDiffPreference;
import com.wms.finance.entity.enums.RateSource;
import com.wms.finance.entity.enums.RateType;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "customers", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinanceCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_currency_id",
            foreignKey = @ForeignKey(name = "fk_customers_default_currency"))
    private Currency defaultCurrency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoicing_currency_id",
            foreignKey = @ForeignKey(name = "fk_customers_invoicing_currency"))
    private Currency invoicingCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 30)
    private RateType rateType;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_source", nullable = false, length = 20)
    private RateSource rateSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "exchange_diff_preference", nullable = false, length = 20)
    private ExchangeDiffPreference exchangeDiffPreference;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}

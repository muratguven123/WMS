package com.wms.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "financial_transactions", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", foreignKey = @ForeignKey(name = "fk_ft_contract"))
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_currency_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ft_original_currency"))
    private Currency originalCurrency;

    @Column(name = "original_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal originalAmount;

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal exchangeRate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_currency_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ft_base_currency"))
    private Currency baseCurrency;

    @Column(name = "converted_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal convertedAmount;

    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate;

    @Column(name = "fallback_rate_used", nullable = false)
    private boolean fallbackRateUsed;
}

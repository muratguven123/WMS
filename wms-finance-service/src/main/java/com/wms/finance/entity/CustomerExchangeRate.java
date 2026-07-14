package com.wms.finance.entity;

import com.wms.finance.entity.enums.RateType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_exchange_rates", schema = "finance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_customer_exchange_rates",
                columnNames = {"customer_id", "source_currency_id", "target_currency_id", "rate_date", "rate_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cer_customer"))
    private FinanceCustomer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_currency_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cer_source_currency"))
    private Currency sourceCurrency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_currency_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cer_target_currency"))
    private Currency targetCurrency;

    @Column(name = "rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal rate;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 30)
    private RateType rateType;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

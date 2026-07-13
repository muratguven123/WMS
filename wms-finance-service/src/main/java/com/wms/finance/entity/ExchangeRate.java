package com.wms.finance.entity;

import com.wms.finance.entity.enums.RateSource;
import com.wms.finance.entity.enums.RateType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rates", schema = "finance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exchange_rate",
                columnNames = {"source_currency_id", "target_currency_id", "rate_date", "rate_type", "rate_source"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_currency_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_er_source_currency"))
    private Currency sourceCurrency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_currency_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_er_target_currency"))
    private Currency targetCurrency;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 30)
    private RateType rateType;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_source", nullable = false, length = 20)
    private RateSource rateSource;

    @Column(name = "rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal rate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

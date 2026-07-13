package com.wms.finance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_rates", schema = "finance",
        indexes = {
                @Index(name = "idx_tax_rate_tax_type", columnList = "tax_type_id"),
                @Index(name = "idx_tax_rate_country", columnList = "country_id"),
                @Index(name = "idx_tax_rate_date_range", columnList = "start_date, end_date"),
                @Index(name = "idx_tax_rate_active", columnList = "is_active")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_type_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_tax_rate_tax_type"))
    private TaxType taxType;

    @Column(name = "country_id", nullable = false)
    private Long countryId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "product_type", length = 50)
    private String productType;

    @Column(name = "operation_type", length = 50)
    private String operationType;

    @Column(name = "rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal rate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

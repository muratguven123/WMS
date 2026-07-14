package com.wms.finance.entity;

import com.wms.finance.entity.enums.AuditActionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rate_audit_logs", schema = "finance",
        indexes = {
                @Index(name = "idx_eral_exchange_rate", columnList = "exchange_rate_id"),
                @Index(name = "idx_eral_changed_at", columnList = "changed_at DESC")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRateAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_rate_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_eral_exchange_rate"))
    private ExchangeRate exchangeRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_fixed_rate_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_eral_contract_fixed_rate"))
    private ContractFixedRate contractFixedRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_exchange_rate_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_eral_customer_exchange_rate"))
    private CustomerExchangeRate customerExchangeRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private AuditActionType actionType;

    @Column(name = "old_rate", precision = 18, scale = 6)
    private BigDecimal oldRate;

    @Column(name = "new_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal newRate;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @PrePersist
    protected void onPrePersist() {
        if (changedAt == null) {
            changedAt = LocalDateTime.now();
        }
    }
}

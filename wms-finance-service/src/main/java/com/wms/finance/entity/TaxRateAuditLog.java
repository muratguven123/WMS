package com.wms.finance.entity;

import com.wms.finance.entity.enums.TaxRateAuditActionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_rate_audit_logs", schema = "finance",
        indexes = {
                @Index(name = "idx_tral_tax_rate", columnList = "tax_rate_id"),
                @Index(name = "idx_tral_changed_at", columnList = "changed_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRateAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_rate_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_tral_tax_rate"))
    private TaxRate taxRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private TaxRateAuditActionType actionType;

    @Column(name = "old_rate", precision = 5, scale = 2)
    private BigDecimal oldRate;

    @Column(name = "new_rate", nullable = false, precision = 5, scale = 2)
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

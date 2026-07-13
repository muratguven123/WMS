package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Tüm kritik işlemlerin denetim kaydı.
 * Immutable — güncelleme ve silme yapılmaz.
 */
@Entity
@Table(name = "transaction_logs",
       indexes = {
           @Index(name = "idx_txlog_company", columnList = "company_id"),
           @Index(name = "idx_txlog_location", columnList = "location_id"),
           @Index(name = "idx_txlog_created", columnList = "created_at_utc")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_txlog_company"))
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_txlog_location"))
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_txlog_user"))
    private User user;

    @Column(name = "action_type", nullable = false, length = 50, updatable = false)
    private String actionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> payload;

    @CreationTimestamp
    @Column(name = "created_at_utc", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAtUtc;
}

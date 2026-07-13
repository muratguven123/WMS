package com.wms.core.entity;

import com.wms.core.entity.enums.ColumnBehavior;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Rol/şirket bazlı tablo kolonu görünürlük kuralı (İş İsteri 16).
 */
@Entity
@Table(
        name = "column_behavior_rules",
        indexes = {
                @Index(name = "idx_cbr_column", columnList = "table_column_def_id"),
                @Index(name = "idx_cbr_role", columnList = "role_id"),
                @Index(name = "idx_cbr_company", columnList = "company_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColumnBehaviorRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "table_column_def_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_cbr_column"))
    private TableColumnDef tableColumnDef;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "company_id")
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "behavior", nullable = false, length = 20)
    private ColumnBehavior behavior;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;
}

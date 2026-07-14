package com.wms.core.entity;

import com.wms.core.entity.enums.ColumnBehavior;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Rol/şirket bazlı tablo kolonu görünürlük kuralı (İş İsteri 16).
 *
 * <p>İş İsteri 2.1 (Madde 8.3) ile depo, müşteri tipi, ürün tipi ve işlem durumu
 * boyutları eklendi. Null boyut = "herkes/her durum için geçerli" semantiği korunur.
 * Kazanan kural: önce spesifiklik (dolu boyut sayısı), eşitlikte {@code priority}.</p>
 */
@Entity
@Table(
        name = "column_behavior_rules",
        indexes = {
                @Index(name = "idx_cbr_column", columnList = "table_column_def_id"),
                @Index(name = "idx_cbr_role", columnList = "role_id"),
                @Index(name = "idx_cbr_company", columnList = "company_id"),
                @Index(name = "idx_cbr_warehouse", columnList = "warehouse_id"),
                @Index(name = "idx_cbr_customer_type", columnList = "customer_type"),
                @Index(name = "idx_cbr_product_type", columnList = "product_type"),
                @Index(name = "idx_cbr_txn_status", columnList = "transaction_status")
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

    /** Hangi depo için geçerli (Location/Zone referansı). Null → tüm depolar. */
    @Column(name = "warehouse_id")
    private Long warehouseId;

    /** Örn: RETAIL, WHOLESALE, ECOMMERCE. Null → tüm müşteri tipleri. */
    @Column(name = "customer_type", length = 50)
    private String customerType;

    /** Örn: STANDARD, HAZMAT, COLD_CHAIN. Null → tüm ürün tipleri. */
    @Column(name = "product_type", length = 50)
    private String productType;

    /** Örn: DRAFT, APPROVED, SHIPPED. Null → tüm işlem durumları. */
    @Column(name = "transaction_status", length = 50)
    private String transactionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "behavior", nullable = false, length = 20)
    private ColumnBehavior behavior;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;
}

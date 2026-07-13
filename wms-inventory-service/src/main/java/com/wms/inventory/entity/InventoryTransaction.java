package com.wms.inventory.entity;

import com.wms.inventory.entity.enums.InventoryTransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 50)
    private InventoryTransactionType transactionType;

    @Column(name = "source_location_id")
    private Long sourceLocationId;

    @Column(name = "target_location_id")
    private Long targetLocationId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "warehouse_location_id")
    private Long warehouseLocationId;

    @Column(name = "product_code", nullable = false, length = 100)
    private String productCode;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "performed_by_user_id", nullable = false)
    private Long performedByUserId;

    @PrePersist
    protected void onCreate() {
        this.transactionDate = LocalDateTime.now();
    }
}

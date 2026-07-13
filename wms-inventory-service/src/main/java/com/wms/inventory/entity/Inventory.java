package com.wms.inventory.entity;

import com.wms.inventory.entity.enums.InventoryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "inventories",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_inventory_location_product_lot_serial",
            columnNames = {"storage_location_id", "product_code", "lot_number", "serial_number", "status"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "product_code", nullable = false, length = 100)
    private String productCode;

    @Column(name = "storage_location_id", nullable = false)
    private Long storageLocationId;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private InventoryStatus status;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "warehouse_location_id", nullable = false)
    private Long warehouseLocationId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

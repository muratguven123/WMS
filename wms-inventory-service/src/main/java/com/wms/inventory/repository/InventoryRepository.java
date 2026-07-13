package com.wms.inventory.repository;

import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.enums.InventoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * Finds active inventory record by unique key: storageLocationId, productCode, and nullable lotNumber/serialNumber.
     * Uses IS NULL conditions to correctly match nulls in database.
     */
    @Query("SELECT i FROM Inventory i WHERE i.storageLocationId = :storageLocationId " +
           "AND i.productCode = :productCode " +
           "AND ((:lotNumber IS NULL AND i.lotNumber IS NULL) OR i.lotNumber = :lotNumber) " +
           "AND ((:serialNumber IS NULL AND i.serialNumber IS NULL) OR i.serialNumber = :serialNumber) " +
           "AND i.status = :status")
    Optional<Inventory> findUniqueInventory(
            @Param("storageLocationId") Long storageLocationId,
            @Param("productCode") String productCode,
            @Param("lotNumber") String lotNumber,
            @Param("serialNumber") String serialNumber,
            @Param("status") InventoryStatus status
    );

    /**
     * FIFO query: Finds available inventory sorted by oldest updatedAt.
     */
    List<Inventory> findByProductCodeAndStatusAndWarehouseLocationIdOrderByUpdatedAtAsc(
            String productCode,
            InventoryStatus status,
            Long warehouseLocationId
    );

    /**
     * FEFO query: Finds available inventory sorted by closest expiryDate.
     * Expiry dates that are NULL are ordered to the end.
     */
    @Query("SELECT i FROM Inventory i WHERE i.productCode = :productCode " +
           "AND i.status = :status " +
           "AND i.warehouseLocationId = :warehouseLocationId " +
           "ORDER BY i.expiryDate ASC NULLS LAST, i.updatedAt ASC")
    List<Inventory> findAvailableStockForFefo(
            @Param("productCode") String productCode,
            @Param("status") InventoryStatus status,
            @Param("warehouseLocationId") Long warehouseLocationId
    );
}

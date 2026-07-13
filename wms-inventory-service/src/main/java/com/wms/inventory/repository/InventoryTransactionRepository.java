package com.wms.inventory.repository;

import com.wms.inventory.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
}

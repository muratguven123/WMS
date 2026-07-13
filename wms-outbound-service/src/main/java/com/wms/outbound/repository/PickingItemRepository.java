package com.wms.outbound.repository;

import com.wms.outbound.entity.PickingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface PickingItemRepository extends JpaRepository<PickingItem, Long> {
}

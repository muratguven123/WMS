package com.wms.outbound.repository;

import com.wms.outbound.entity.OutboundOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface OutboundOrderItemRepository extends JpaRepository<OutboundOrderItem, Long> {
}

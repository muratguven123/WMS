package com.wms.inbound.repository;

import com.wms.inbound.entity.InboundOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface InboundOrderItemRepository extends JpaRepository<InboundOrderItem, Long> {
}

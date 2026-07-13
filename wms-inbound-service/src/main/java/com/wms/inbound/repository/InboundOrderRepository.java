package com.wms.inbound.repository;

import com.wms.inbound.entity.InboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InboundOrderRepository extends JpaRepository<InboundOrder, Long> {

    Optional<InboundOrder> findByOrderNumber(String orderNumber);

    @Query("SELECT o FROM InboundOrder o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<InboundOrder> findByIdWithItems(@Param("id") Long id);
}

package com.wms.outbound.repository;

import com.wms.outbound.entity.OutboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OutboundOrderRepository extends JpaRepository<OutboundOrder, Long> {

    Optional<OutboundOrder> findByOrderNumber(String orderNumber);

    @Query("SELECT DISTINCT o FROM OutboundOrder o LEFT JOIN FETCH o.items WHERE o.id IN :ids")
    List<OutboundOrder> findAllByIdWithItems(@Param("ids") List<Long> ids);
}

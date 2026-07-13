package com.wms.outbound.repository;

import com.wms.outbound.entity.Shipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    Optional<Shipment> findByShipmentNumber(String shipmentNumber);

    @EntityGraph(attributePaths = {"items"})
    Optional<Shipment> findWithDetailsById(Long id);

    Page<Shipment> findAll(Pageable pageable);

    Page<Shipment> findByCompanyIdAndWarehouseLocationId(
            Long companyId, Long warehouseLocationId, Pageable pageable);
}

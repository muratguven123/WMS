package com.wms.inbound.repository;

import com.wms.inbound.entity.Receipt;
import com.wms.inbound.entity.enums.ReceiptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findByReceiptNumber(String receiptNumber);

    @EntityGraph(attributePaths = {"items", "inboundOrder", "inboundOrder.items"})
    Optional<Receipt> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"items", "inboundOrder"})
    Page<Receipt> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"items", "inboundOrder"})
    Page<Receipt> findByStatus(ReceiptStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "inboundOrder"})
    Page<Receipt> findByInboundOrder_CompanyIdAndInboundOrder_WarehouseLocationId(
            Long companyId, Long warehouseLocationId, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "inboundOrder"})
    Page<Receipt> findByInboundOrder_CompanyIdAndInboundOrder_WarehouseLocationIdAndStatus(
            Long companyId, Long warehouseLocationId, ReceiptStatus status, Pageable pageable);
}

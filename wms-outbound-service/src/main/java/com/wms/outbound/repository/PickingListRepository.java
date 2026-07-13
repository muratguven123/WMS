package com.wms.outbound.repository;

import com.wms.outbound.entity.PickingList;
import com.wms.outbound.entity.enums.PickingListStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PickingListRepository extends JpaRepository<PickingList, Long> {

    @EntityGraph(attributePaths = {"items", "items.outboundOrderItem", "items.outboundOrderItem.outboundOrder"})
    Optional<PickingList> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = "items")
    Page<PickingList> findByCompanyIdAndWarehouseLocationId(
            Long companyId, Long warehouseLocationId, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.outboundOrderItem"})
    List<PickingList> findByCompanyIdAndWarehouseLocationIdAndAssignedUserIdAndStatusIn(
            Long companyId, Long warehouseLocationId, Long assignedUserId, List<PickingListStatus> statuses);

    @EntityGraph(attributePaths = {"items", "items.outboundOrderItem"})
    List<PickingList> findByCompanyIdAndWarehouseLocationIdAndAssignedUserIdIsNullAndStatus(
            Long companyId, Long warehouseLocationId, PickingListStatus status);

    @EntityGraph(attributePaths = {"items", "items.outboundOrderItem"})
    List<PickingList> findByAssignedUserIdAndStatusIn(Long assignedUserId, List<PickingListStatus> statuses);

    @EntityGraph(attributePaths = {"items", "items.outboundOrderItem"})
    List<PickingList> findByAssignedUserIdIsNullAndStatus(PickingListStatus status);

    @Query("SELECT pl FROM PickingList pl JOIN pl.items item WHERE item.id = :itemId")
    @EntityGraph(attributePaths = {"items", "items.outboundOrderItem"})
    Optional<PickingList> findByItemId(@Param("itemId") Long itemId);
}

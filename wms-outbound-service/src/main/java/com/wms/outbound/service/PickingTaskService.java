package com.wms.outbound.service;

import com.wms.outbound.dto.PickingListResponse;
import com.wms.outbound.entity.PickingItem;
import com.wms.outbound.entity.PickingList;
import com.wms.outbound.entity.enums.PickingItemStatus;
import com.wms.outbound.entity.enums.PickingListStatus;
import com.wms.outbound.exception.BusinessException;
import com.wms.outbound.messaging.TaskEventPublisher;
import com.wms.outbound.repository.PickingListRepository;
import com.wms.outbound.security.TenantContextHolder;
import com.wms.outbound.security.TenantScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PickingTaskService {

    private final PickingListRepository pickingListRepository;
    private final PickingRoutingService pickingRoutingService;
    private final TaskEventPublisher taskEventPublisher;

    @Transactional(readOnly = true)
    public List<PickingListResponse> listMyTasks() {
        Long userId = TenantContextHolder.getUserId();
        Long companyId = TenantScopeGuard.requireCompanyId();
        Long warehouseLocationId = TenantScopeGuard.requireWarehouseLocationId();
        return pickingListRepository
                .findByCompanyIdAndWarehouseLocationIdAndAssignedUserIdAndStatusIn(
                        companyId,
                        warehouseLocationId,
                        userId,
                        List.of(PickingListStatus.ASSIGNED, PickingListStatus.IN_PROGRESS))
                .stream()
                .map(pickingRoutingService::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PickingListResponse> listUnassigned() {
        Long companyId = TenantScopeGuard.requireCompanyId();
        Long warehouseLocationId = TenantScopeGuard.requireWarehouseLocationId();
        return pickingListRepository
                .findByCompanyIdAndWarehouseLocationIdAndAssignedUserIdIsNullAndStatus(
                        companyId, warehouseLocationId, PickingListStatus.PENDING)
                .stream()
                .map(pickingRoutingService::mapToResponse)
                .toList();
    }

    @Transactional
    public PickingListResponse assign(Long pickingListId, Long assignedUserId) {
        PickingList list = requireList(pickingListId);
        if (list.getStatus() == PickingListStatus.COMPLETED) {
            throw new BusinessException("Completed picking list cannot be assigned", HttpStatus.BAD_REQUEST);
        }

        list.setAssignedUserId(assignedUserId);
        list.setAssignedAt(LocalDateTime.now());
        list.setStatus(PickingListStatus.ASSIGNED);

        PickingList saved = pickingListRepository.save(list);
        taskEventPublisher.publishAssigned(saved);
        return pickingRoutingService.mapToResponse(saved);
    }

    @Transactional
    public PickingListResponse start(Long pickingListId) {
        Long userId = TenantContextHolder.getUserId();
        PickingList list = requireList(pickingListId);

        if (list.getAssignedUserId() != null && !list.getAssignedUserId().equals(userId)) {
            throw new BusinessException("Task is assigned to another operator", HttpStatus.FORBIDDEN);
        }

        if (list.getAssignedUserId() == null) {
            list.setAssignedUserId(userId);
            list.setAssignedAt(LocalDateTime.now());
        }

        list.setStatus(PickingListStatus.IN_PROGRESS);
        PickingList saved = pickingListRepository.save(list);
        taskEventPublisher.publishAssigned(saved);
        return pickingRoutingService.mapToResponse(saved);
    }

    @Transactional
    public PickingListResponse confirmPick(Long itemId, BigDecimal pickedQty) {
        Long userId = TenantContextHolder.getUserId();
        PickingList list = pickingListRepository.findByItemId(itemId)
                .orElseThrow(() -> new BusinessException("Picking item not found", HttpStatus.NOT_FOUND));

        TenantScopeGuard.assertEntityBelongsToContext(list.getWarehouseLocationId(), list.getCompanyId());

        if (list.getAssignedUserId() != null && !list.getAssignedUserId().equals(userId)) {
            throw new BusinessException("Task is assigned to another operator", HttpStatus.FORBIDDEN);
        }

        PickingItem item = list.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Picking item not found on list", HttpStatus.NOT_FOUND));

        if (pickedQty == null || pickedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Picked quantity must be positive", HttpStatus.BAD_REQUEST);
        }

        BigDecimal newPicked = item.getPickedQuantity().add(pickedQty);
        if (newPicked.compareTo(item.getQuantityToPick()) > 0) {
            throw new BusinessException("Picked quantity exceeds required amount", HttpStatus.BAD_REQUEST);
        }

        item.setPickedQuantity(newPicked);
        item.setStatus(newPicked.compareTo(item.getQuantityToPick()) >= 0
                ? PickingItemStatus.PICKED
                : PickingItemStatus.PENDING);

        if (list.getStatus() == PickingListStatus.PENDING || list.getStatus() == PickingListStatus.ASSIGNED) {
            list.setStatus(PickingListStatus.IN_PROGRESS);
            if (list.getAssignedUserId() == null) {
                list.setAssignedUserId(userId);
                list.setAssignedAt(LocalDateTime.now());
            }
        }

        boolean allPicked = list.getItems().stream()
                .allMatch(i -> i.getStatus() == PickingItemStatus.PICKED);
        if (allPicked) {
            list.setStatus(PickingListStatus.COMPLETED);
            taskEventPublisher.publishCompleted(list, userId);
        } else {
            taskEventPublisher.publishProgress(list, item, userId);
        }

        PickingList saved = pickingListRepository.save(list);
        return pickingRoutingService.mapToResponse(saved);
    }

    private PickingList requireList(Long pickingListId) {
        PickingList list = pickingListRepository.findWithDetailsById(pickingListId)
                .orElseThrow(() -> new BusinessException("Picking list not found", HttpStatus.NOT_FOUND));
        TenantScopeGuard.assertEntityBelongsToContext(list.getWarehouseLocationId(), list.getCompanyId());
        return list;
    }
}

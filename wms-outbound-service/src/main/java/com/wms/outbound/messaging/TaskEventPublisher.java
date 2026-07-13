package com.wms.outbound.messaging;

import com.wms.events.DomainEvent;
import com.wms.events.EventType;
import com.wms.events.KafkaTopics;
import com.wms.outbound.entity.PickingItem;
import com.wms.outbound.entity.PickingList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class TaskEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishAssigned(PickingList list) {
        Map<String, Object> payload = basePayload(list);
        payload.put("assignedUserId", list.getAssignedUserId() != null ? list.getAssignedUserId().toString() : null);
        payload.put("routeItems", routeSnapshot(list));

        publish(EventType.TASK_ASSIGNED, KafkaTopics.TASK_ASSIGNED, list, payload);
        publishOperatorStatus(list, "ASSIGNED");
    }

    public void publishProgress(PickingList list, PickingItem item, Long operatorUserId) {
        Map<String, Object> payload = basePayload(list);
        payload.put("itemId", item.getId().toString());
        payload.put("pickedQty", item.getPickedQuantity());
        payload.put("operatorUserId", operatorUserId.toString());
        payload.put("nextBinId", nextPendingBin(list));

        publish(EventType.TASK_PROGRESS, KafkaTopics.TASK_PROGRESS, list, payload);
        publishOperatorStatus(list, "IN_PROGRESS");
    }

    public void publishCompleted(PickingList list, Long completedBy) {
        Map<String, Object> payload = basePayload(list);
        payload.put("completedBy", completedBy.toString());

        publish(EventType.TASK_COMPLETED, KafkaTopics.TASK_COMPLETED, list, payload);
        publishOperatorStatus(list, "COMPLETED");
    }

    private void publishOperatorStatus(PickingList list, String operatorStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("operatorUserId", list.getAssignedUserId() != null ? list.getAssignedUserId().toString() : null);
        payload.put("status", operatorStatus);
        payload.put("pickingListId", list.getId().toString());
        payload.put("activeTaskCount", "IN_PROGRESS".equals(operatorStatus) ? 1 : 0);

        DomainEvent event = DomainEvent.of(
                EventType.OPERATOR_STATUS_UPDATED,
                list.getCompanyId(),
                list.getWarehouseLocationId(),
                payload);
        send(KafkaTopics.OPERATOR_STATUS_UPDATED, event);
    }

    private void publish(EventType type, String topic, PickingList list, Map<String, Object> payload) {
        DomainEvent event = DomainEvent.of(type, list.getCompanyId(), list.getWarehouseLocationId(), payload);
        send(topic, event);
    }

    private void send(String topic, DomainEvent event) {
        String key = event.companyId() + ":" + event.locationId();
        kafkaTemplate.send(topic, key, event);
    }

    private Map<String, Object> basePayload(PickingList list) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", list.getId().toString());
        payload.put("pickingListId", list.getId().toString());
        payload.put("status", list.getStatus().name());
        return payload;
    }

    private List<Map<String, Object>> routeSnapshot(PickingList list) {
        return list.getItems().stream().map(item -> {
            Map<String, Object> row = new HashMap<>();
            row.put("itemId", item.getId().toString());
            row.put("addressCode", item.getAddressCode());
            row.put("productCode", item.getOutboundOrderItem().getProductCode());
            row.put("quantityToPick", item.getQuantityToPick());
            return row;
        }).toList();
    }

    private String nextPendingBin(PickingList list) {
        return list.getItems().stream()
                .filter(item -> item.getPickedQuantity().compareTo(item.getQuantityToPick()) < 0)
                .map(item -> item.getSourceLocationId().toString())
                .findFirst()
                .orElse(null);
    }
}

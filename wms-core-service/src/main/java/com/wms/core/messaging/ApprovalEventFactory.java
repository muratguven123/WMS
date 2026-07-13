package com.wms.core.messaging;

import com.wms.core.entity.ApprovalRequest;
import com.wms.events.DomainEvent;
import com.wms.events.EventType;
import com.wms.events.KafkaTopics;
import com.wms.core.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ApprovalEventFactory {

    private final DomainEventPublisher domainEventPublisher;

    public void publishCreated(ApprovalRequest request) {
        publish(request, EventType.APPROVAL_CREATED, KafkaTopics.APPROVAL_CREATED);
    }

    public void publishResolved(ApprovalRequest request) {
        publish(request, EventType.APPROVAL_RESOLVED, KafkaTopics.APPROVAL_RESOLVED);
    }

    private void publish(ApprovalRequest request, EventType type, String topic) {
        Long companyId = TenantContextHolder.getCompanyId();
        Long locationId = TenantContextHolder.getLocationId();

        Map<String, Object> payload = new HashMap<>();
        payload.put("approvalRequestId", request.getId().toString());
        payload.put("referenceType", request.getReferenceType());
        payload.put("referenceId", request.getReferenceId().toString());
        payload.put("status", request.getStatus().name());
        payload.put("requestedByUserId", request.getRequestedByUserId().toString());
        payload.put("stepConfigId", request.getStepConfigId().toString());

        DomainEvent event = DomainEvent.of(type, companyId, locationId, payload);
        domainEventPublisher.publish(topic, event);
    }
}

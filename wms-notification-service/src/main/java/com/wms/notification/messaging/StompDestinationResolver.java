package com.wms.notification.messaging;

import com.wms.events.DomainEvent;
import com.wms.events.EventType;
import com.wms.events.StompDestinations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StompDestinationResolver {

    public List<String> resolve(DomainEvent event) {
        if (event.companyId() == null) {
            return List.of();
        }

        List<String> destinations = new ArrayList<>();

        switch (event.eventType()) {
            case STOCK_CHANGED -> {
                if (event.locationId() != null) {
                    destinations.add(StompDestinations.stock(event.companyId(), event.locationId()));
                }
            }
            case LOCATION_UPDATED -> {
                if (event.locationId() != null) {
                    destinations.add(StompDestinations.locations(event.companyId(), event.locationId()));
                }
            }
            case APPROVAL_CREATED, APPROVAL_RESOLVED -> {
                destinations.add(StompDestinations.approvals(event.companyId()));
                Long requestedBy = uuidFromPayload(event, "requestedByUserId");
                if (requestedBy != null) {
                    destinations.add(StompDestinations.userApprovals(requestedBy));
                }
            }
            case INTEGRATION_LOG_UPDATED -> destinations.add(StompDestinations.integrations(event.companyId()));
            case TASK_ASSIGNED -> {
                if (event.locationId() != null) {
                    destinations.add(StompDestinations.tasks(event.companyId(), event.locationId()));
                }
                Long assignedUser = uuidFromPayload(event, "assignedUserId");
                if (assignedUser != null) {
                    destinations.add(StompDestinations.userTasks(assignedUser));
                }
            }
            case TASK_PROGRESS -> {
                if (event.locationId() != null) {
                    destinations.add(StompDestinations.tasks(event.companyId(), event.locationId()));
                }
                Long operatorId = uuidFromPayload(event, "operatorUserId");
                if (operatorId != null) {
                    destinations.add(StompDestinations.userTasks(operatorId));
                }
            }
            case TASK_COMPLETED -> {
                if (event.locationId() != null) {
                    destinations.add(StompDestinations.tasks(event.companyId(), event.locationId()));
                }
            }
            case OPERATOR_STATUS_UPDATED -> {
                if (event.locationId() != null) {
                    destinations.add(StompDestinations.operators(event.companyId(), event.locationId()));
                }
            }
            default -> { }
        }

        return destinations;
    }

    private Long uuidFromPayload(DomainEvent event, String key) {
        if (event.payload() == null || !event.payload().containsKey(key)) {
            return null;
        }
        Object value = event.payload().get(key);
        if (value == null) {
            return null;
        }
        return Long.parseLong(value.toString());
    }
}

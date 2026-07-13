package com.wms.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainEvent(
        Long eventId,
        EventType eventType,
        OffsetDateTime occurredAt,
        Long companyId,
        Long locationId,
        Map<String, Object> payload
) {
    private static final AtomicLong EVENT_ID_SEQ = new AtomicLong(System.currentTimeMillis());

    public static DomainEvent of(EventType type, Long companyId, Long locationId, Map<String, Object> payload) {
        return new DomainEvent(
                EVENT_ID_SEQ.incrementAndGet(),
                type,
                OffsetDateTime.now(),
                companyId,
                locationId,
                payload);
    }
}

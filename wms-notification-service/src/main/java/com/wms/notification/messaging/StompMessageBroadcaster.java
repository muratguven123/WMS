package com.wms.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.events.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StompMessageBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final StompDestinationResolver destinationResolver;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${wms.redis.relay-channel:wms:stomp:relay}")
    private String relayChannel;

    public void broadcast(DomainEvent event) {
        List<String> destinations = destinationResolver.resolve(event);
        if (destinations.isEmpty()) {
            log.debug("No STOMP destinations for event type={}", event.eventType());
            return;
        }

        for (String destination : destinations) {
            publishToRedis(destination, event);
        }
    }

    public void deliverLocally(String destination, DomainEvent event) {
        messagingTemplate.convertAndSend(destination, event);
        log.debug("STOMP delivered to {} eventType={}", destination, event.eventType());
    }

    private void publishToRedis(String destination, DomainEvent event) {
        try {
            RelayMessage relay = new RelayMessage(destination, event);
            redisTemplate.convertAndSend(relayChannel, objectMapper.writeValueAsString(relay));
        } catch (Exception ex) {
            log.error("Redis relay failed, delivering locally only: {}", ex.getMessage());
            deliverLocally(destination, event);
        }
    }

    public record RelayMessage(String destination, DomainEvent event) {
    }
}

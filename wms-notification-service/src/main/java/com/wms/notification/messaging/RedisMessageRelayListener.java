package com.wms.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageRelayListener implements MessageListener {

    private final StompMessageBroadcaster stompMessageBroadcaster;
    private final ObjectMapper objectMapper;

    @Value("${wms.redis.relay-channel:wms:stomp:relay}")
    private String relayChannel;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            StompMessageBroadcaster.RelayMessage relay = objectMapper.readValue(
                    message.getBody(), StompMessageBroadcaster.RelayMessage.class);
            stompMessageBroadcaster.deliverLocally(relay.destination(), relay.event());
        } catch (Exception ex) {
            log.error("Failed to process Redis relay message: {}", ex.getMessage());
        }
    }

    public String getRelayChannel() {
        return relayChannel;
    }
}

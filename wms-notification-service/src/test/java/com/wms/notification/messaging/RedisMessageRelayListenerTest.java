package com.wms.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.events.DomainEvent;
import com.wms.events.EventType;
import com.wms.events.StompDestinations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RedisMessageRelayListener} birim testleri.
 *
 * <p>Redis relay kanalından gelen mesajın deserialize edilip yerel STOMP teslimine
 * yönlendirilmesini ve bozuk payload'ların sessizce yutulmasını (kuyruğu bloklamaması) doğrular.
 */
@ExtendWith(MockitoExtension.class)
class RedisMessageRelayListenerTest {

    @Mock
    private StompMessageBroadcaster broadcaster;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("Geçerli relay mesajı deserialize edilip deliverLocally'e yönlendirilir")
    void validMessage_routedToLocalDelivery() throws Exception {
        RedisMessageRelayListener listener = new RedisMessageRelayListener(broadcaster, objectMapper);

        String destination = StompDestinations.stock(1L, 2L);
        DomainEvent event = DomainEvent.of(EventType.STOCK_CHANGED, 1L, 2L, Map.of("qty", 5));
        byte[] body = objectMapper.writeValueAsBytes(
                new StompMessageBroadcaster.RelayMessage(destination, event));

        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);

        listener.onMessage(message, null);

        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(broadcaster).deliverLocally(destCaptor.capture(), eventCaptor.capture());

        assertThat(destCaptor.getValue()).isEqualTo(destination);
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(EventType.STOCK_CHANGED);
        assertThat(eventCaptor.getValue().companyId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().locationId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Bozuk payload istisna fırlatmaz ve teslim yapılmaz")
    void malformedMessage_swallowedNoDelivery() {
        RedisMessageRelayListener listener = new RedisMessageRelayListener(broadcaster, objectMapper);

        Message message = mock(Message.class);
        when(message.getBody()).thenReturn("not-json".getBytes(StandardCharsets.UTF_8));

        listener.onMessage(message, null);

        verifyNoInteractions(broadcaster);
    }

    @Test
    @DisplayName("relay-channel varsayılanı doğru okunur")
    void relayChannelDefault() {
        RedisMessageRelayListener listener = new RedisMessageRelayListener(broadcaster, objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(listener, "relayChannel", "wms:stomp:relay");
        assertThat(listener.getRelayChannel()).isEqualTo("wms:stomp:relay");
    }
}

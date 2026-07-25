package com.wms.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.events.DomainEvent;
import com.wms.events.EventType;
import com.wms.events.StompDestinations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link StompMessageBroadcaster} birim testleri.
 *
 * <p>Redis relay yayınını, hedef bulunamadığında sessiz kalmayı ve Redis hata durumunda
 * yerel teslim fallback'ini doğrular. Mockito ile izole — Docker/Redis gerektirmez.
 */
@ExtendWith(MockitoExtension.class)
class StompMessageBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private StompDestinationResolver destinationResolver;
    @Mock
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private StompMessageBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        // Lombok @RequiredArgsConstructor alan sırası: messagingTemplate, destinationResolver, redisTemplate, objectMapper
        broadcaster = new StompMessageBroadcaster(messagingTemplate, destinationResolver, redisTemplate, objectMapper);
        ReflectionTestUtils.setField(broadcaster, "relayChannel", "wms:stomp:relay");
    }

    private static DomainEvent stockEvent() {
        return DomainEvent.of(EventType.STOCK_CHANGED, 1L, 2L, Map.of("qty", 5));
    }

    @Test
    @DisplayName("Hedef yoksa Redis'e hiçbir şey yayınlanmaz")
    void noDestinations_publishesNothing() {
        DomainEvent event = stockEvent();
        when(destinationResolver.resolve(event)).thenReturn(List.of());

        broadcaster.broadcast(event);

        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Her hedef için Redis relay kanalına bir mesaj yayınlanır")
    void withDestinations_publishesToRedisPerDestination() {
        DomainEvent event = stockEvent();
        String d1 = StompDestinations.stock(1L, 2L);
        String d2 = StompDestinations.locations(1L, 2L);
        when(destinationResolver.resolve(event)).thenReturn(List.of(d1, d2));

        broadcaster.broadcast(event);

        verify(redisTemplate, times(2)).convertAndSend(eq("wms:stomp:relay"), any(String.class));
        // Redis yolu başarılıyken yerel teslim yapılmaz.
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Redis yayını hata verirse yerel teslime düşülür")
    void redisFailure_fallsBackToLocalDelivery() {
        DomainEvent event = stockEvent();
        String destination = StompDestinations.stock(1L, 2L);
        when(destinationResolver.resolve(event)).thenReturn(List.of(destination));
        doThrow(new RuntimeException("redis down"))
                .when(redisTemplate).convertAndSend(any(String.class), any(String.class));

        broadcaster.broadcast(event);

        verify(messagingTemplate).convertAndSend(eq(destination), eq(event));
    }

    @Test
    @DisplayName("deliverLocally doğrudan SimpMessagingTemplate'e gönderir")
    void deliverLocally_sendsToMessagingTemplate() {
        DomainEvent event = stockEvent();
        String destination = StompDestinations.stock(1L, 2L);

        broadcaster.deliverLocally(destination, event);

        verify(messagingTemplate).convertAndSend(eq(destination), eq(event));
        verify(redisTemplate, never()).convertAndSend(any(String.class), any(String.class));
    }
}

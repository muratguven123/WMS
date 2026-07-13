package com.wms.inbound.listener;

import com.wms.inbound.dto.ReceiptApprovedEvent;
import com.wms.inbound.dto.ReceiptItemEventDto;
import com.wms.inbound.service.ReceiptEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReceiptEventListener")
class ReceiptEventListenerTest {

    @Mock
    private ReceiptEventPublisher receiptEventPublisher;

    @InjectMocks
    private ReceiptEventListener receiptEventListener;

    @Test
    @DisplayName("ReceiptApprovedEvent'i değiştirmeden publisher'a iletir")
    void handleReceiptApproved_delegatesUnmodifiedEventToPublisher() {
        Long receiptId = 1L;
        Long inboundOrderId = 1L;
        Long companyId = 1L;
        Long warehouseLocationId = 1L;
        Instant approvedAt = Instant.parse("2026-07-04T12:00:00Z");
        List<ReceiptItemEventDto> items = List.of(
                new ReceiptItemEventDto("PROD_001", new BigDecimal("5.0000"), "LOT1", "SN1", null),
                new ReceiptItemEventDto("PROD_002", BigDecimal.ONE, "", "", null)
        );
        ReceiptApprovedEvent event = new ReceiptApprovedEvent(
                receiptId, inboundOrderId, companyId, warehouseLocationId, approvedAt, items);

        receiptEventListener.handleReceiptApproved(event);

        ArgumentCaptor<ReceiptApprovedEvent> eventCaptor = ArgumentCaptor.forClass(ReceiptApprovedEvent.class);
        verify(receiptEventPublisher, times(1)).publishReceiptApproved(eventCaptor.capture());
        verifyNoMoreInteractions(receiptEventPublisher);

        ReceiptApprovedEvent published = eventCaptor.getValue();
        assertThat(published).isSameAs(event);
        assertThat(published.receiptId()).isEqualTo(receiptId);
        assertThat(published.inboundOrderId()).isEqualTo(inboundOrderId);
        assertThat(published.companyId()).isEqualTo(companyId);
        assertThat(published.warehouseLocationId()).isEqualTo(warehouseLocationId);
        assertThat(published.approvedAt()).isEqualTo(approvedAt);
        assertThat(published.items()).hasSize(2);
    }
}

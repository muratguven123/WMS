package com.wms.inbound.listener;

import com.wms.inbound.dto.ReceiptApprovedEvent;
import com.wms.inbound.service.ReceiptEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptEventListener {

    private final ReceiptEventPublisher receiptEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReceiptApproved(ReceiptApprovedEvent event) {
        log.info("Handling local ReceiptApprovedEvent after commit for receipt ID: {}", event.receiptId());
        receiptEventPublisher.publishReceiptApproved(event);
    }
}

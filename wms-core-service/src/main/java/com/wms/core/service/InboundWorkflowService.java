package com.wms.core.service;

import com.wms.core.aspect.annotation.CheckWorkflowStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


/**
 * Mal kabul süreci — {@link CheckWorkflowStep} kullanım örneği.
 */
@Slf4j
@Service
public class InboundWorkflowService {

    @CheckWorkflowStep(
            processCode = "INBOUND",
            stepCode = "QC",
            referenceIdParam = "receiptId",
            referenceType = "RECEIPT"
    )
    public void completeQcStep(Long receiptId, String result) {
        log.info("[Inbound] QC adımı tamamlandı. receiptId={} result={}", receiptId, result);
    }

    @CheckWorkflowStep(
            processCode = "INBOUND",
            stepCode = "PUTAWAY",
            referenceIdParam = "receiptId",
            referenceType = "RECEIPT"
    )
    public void completePutawayStep(Long receiptId) {
        log.info("[Inbound] Putaway adımı tamamlandı. receiptId={}", receiptId);
    }
}

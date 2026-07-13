package com.wms.core.exception;


/**
 * Bir süreç adımında {@code requiresApproval = true} olduğunda fırlatılır.
 * İşlem askıya alınmış; {@link #approvalRequestId} ile onay talebi takip edilebilir.
 *
 * <p>HTTP 202 Accepted olarak yanıtlanır — işlem hatalı değil, onay bekliyor.</p>
 */
public class ApprovalRequiredException extends RuntimeException {

    private final Long approvalRequestId;

    public ApprovalRequiredException(Long approvalRequestId) {
        super("İşlem onay bekliyor. approvalRequestId=" + approvalRequestId);
        this.approvalRequestId = approvalRequestId;
    }

    public Long getApprovalRequestId() {
        return approvalRequestId;
    }
}

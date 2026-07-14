package com.wms.outbound.exception;

import org.springframework.http.HttpStatus;

/**
 * Core workflow adımı onay beklediğinde fırlatılır (HTTP 202).
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

    public HttpStatus getStatus() {
        return HttpStatus.ACCEPTED;
    }
}

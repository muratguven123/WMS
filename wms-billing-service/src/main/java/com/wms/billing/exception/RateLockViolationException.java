package com.wms.billing.exception;


public class RateLockViolationException extends RuntimeException {

    public RateLockViolationException(Long invoiceId) {
        super("Onaylanmış faturanın kur bilgisi değiştirilemez. invoiceId=%s".formatted(invoiceId));
    }
}

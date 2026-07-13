package com.wms.billing.exception;


public class InvoiceNotFoundException extends RuntimeException {

    public InvoiceNotFoundException(Long invoiceId) {
        super("Fatura bulunamadı: id=%s".formatted(invoiceId));
    }
}

package com.wms.finance.exception;

public class TaxRateNotFoundException extends RuntimeException {

    public TaxRateNotFoundException(String message) {
        super(message);
    }
}

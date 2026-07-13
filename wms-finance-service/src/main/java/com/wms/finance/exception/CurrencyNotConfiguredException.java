package com.wms.finance.exception;

public class CurrencyNotConfiguredException extends RuntimeException {

    public CurrencyNotConfiguredException() {
        super("No currency could be resolved for the transaction context");
    }
}

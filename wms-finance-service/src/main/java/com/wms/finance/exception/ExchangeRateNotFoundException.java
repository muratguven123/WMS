package com.wms.finance.exception;

public class ExchangeRateNotFoundException extends RuntimeException {

    public ExchangeRateNotFoundException(String source, String target, String rateType) {
        super("Exchange rate not found within fallback window: "
                + source + " -> " + target + " (" + rateType + ")");
    }
}

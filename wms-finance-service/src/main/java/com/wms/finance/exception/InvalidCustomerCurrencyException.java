package com.wms.finance.exception;


public class InvalidCustomerCurrencyException extends RuntimeException {

    private final Long customerId;
    private final Long currencyId;

    public InvalidCustomerCurrencyException(Long customerId, Long currencyId) {
        super("Currency [%s] is not permitted for customer [%s]".formatted(currencyId, customerId));
        this.customerId = customerId;
        this.currencyId = currencyId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getCurrencyId() {
        return currencyId;
    }
}

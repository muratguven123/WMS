package com.wms.finance.service;


public record TaxResolutionContext(
        Long countryId,
        Long locationId,
        Long customerId,
        String productType,
        String operationType
) {

    public static TaxResolutionContext ofCountry(Long countryId) {
        return new TaxResolutionContext(countryId, null, null, null, null);
    }

    public TaxResolutionContext {
        if (countryId == null) {
            throw new IllegalArgumentException("countryId is required for tax resolution");
        }
    }
}

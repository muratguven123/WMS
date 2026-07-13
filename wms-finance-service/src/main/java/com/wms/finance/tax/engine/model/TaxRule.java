package com.wms.finance.tax.engine.model;

/**
 * Bir vergi kuralı: hangi stratejiyle, hangi oran ve muafiyet bilgileriyle
 * hesaplama yapılacağını tanımlar.
 *
 * @param taxTypeCode       audit log için vergi tipi kodu (örn. KDV_20)
 * @param rateLookupCode    temporal oran çözümlemesi için kod (örn. KDV); null ise taxTypeCode kullanılır
 */
public record TaxRule(
        String taxTypeCode,
        String rateLookupCode,
        TaxStrategyType strategyType,
        boolean exempt,
        String exemptionCode,
        String calculationSource
) {
    public TaxRule {
        if (taxTypeCode == null || taxTypeCode.isBlank())
            throw new IllegalArgumentException("taxTypeCode boş olamaz.");
        if (strategyType == null)
            throw new IllegalArgumentException("strategyType null olamaz.");
        if (exempt && (exemptionCode == null || exemptionCode.isBlank()))
            throw new IllegalArgumentException("Muafiyet tanımlandığında exemptionCode zorunludur.");
        if (!exempt && exemptionCode != null)
            throw new IllegalArgumentException("Muafiyet yokken exemptionCode set edilemez.");
        if (calculationSource == null || calculationSource.isBlank())
            throw new IllegalArgumentException("calculationSource boş olamaz.");
    }

    public String effectiveRateLookupCode() {
        return rateLookupCode != null && !rateLookupCode.isBlank() ? rateLookupCode : taxTypeCode;
    }

    public static TaxRule of(String taxTypeCode, TaxStrategyType strategyType, String source) {
        return new TaxRule(taxTypeCode, null, strategyType, false, null, source);
    }

    public static TaxRule of(String taxTypeCode, String rateLookupCode,
                             TaxStrategyType strategyType, String source) {
        return new TaxRule(taxTypeCode, rateLookupCode, strategyType, false, null, source);
    }

    public static TaxRule exempt(String taxTypeCode, TaxStrategyType strategyType,
                                 String exemptionCode, String source) {
        return new TaxRule(taxTypeCode, null, strategyType, true, exemptionCode, source);
    }
}

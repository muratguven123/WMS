package com.wms.finance.tax.engine.model;

/**
 * Engine'in factory üzerinden çözümlediği strateji türleri.
 */
public enum TaxStrategyType {

    /** Vergi matrah üzerine eklenir (KDV Hariç). */
    EXCLUSIVE,

    /** Vergi brüt tutara dahildir (KDV Dahil). */
    INCLUSIVE,

    /** Katmanlı / iç içe vergi uygulaması (ÖTV → KDV). */
    COMPOUND
}

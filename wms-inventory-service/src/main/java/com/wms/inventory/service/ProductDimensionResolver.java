package com.wms.inventory.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ProductDimensionResolver {

    /**
     * Resolves the volume of a single product unit.
     */
    public BigDecimal getUnitVolume(String productCode) {
        if (productCode != null && productCode.startsWith("HEAVY")) {
            return new BigDecimal("0.5000"); // 0.5 cubic meters
        }
        return new BigDecimal("0.0100"); // default 0.01 cubic meters
    }

    /**
     * Resolves the weight of a single product unit.
     */
    public BigDecimal getUnitWeight(String productCode) {
        if (productCode != null && productCode.startsWith("HEAVY")) {
            return new BigDecimal("50.0000"); // 50 kg
        }
        return new BigDecimal("1.0000"); // default 1 kg
    }
}

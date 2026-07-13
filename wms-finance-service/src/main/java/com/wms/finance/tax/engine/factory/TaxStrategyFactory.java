package com.wms.finance.tax.engine.factory;

import com.wms.finance.tax.engine.model.TaxStrategyType;
import com.wms.finance.tax.strategy.CompoundTaxStrategy;
import com.wms.finance.tax.strategy.TaxCalculationStrategy;
import com.wms.finance.tax.strategy.TaxExclusiveStrategy;
import com.wms.finance.tax.strategy.TaxInclusiveStrategy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link TaxStrategyType} enum değerinden doğru {@link TaxCalculationStrategy}
 * implementasyonunu çözen factory.
 *
 * <p>COMPOUND tip için varsayılan olarak iki katmanlı (exclusive → exclusive)
 * yapı döner. Gerçek proje senaryolarında katman konfigürasyonu
 * dış kaynaktan (DB, config) okunabilir.
 */
@Component
public class TaxStrategyFactory {

    private final TaxExclusiveStrategy exclusiveStrategy;
    private final TaxInclusiveStrategy inclusiveStrategy;

    public TaxStrategyFactory(TaxExclusiveStrategy exclusiveStrategy,
                               TaxInclusiveStrategy inclusiveStrategy) {
        this.exclusiveStrategy = exclusiveStrategy;
        this.inclusiveStrategy = inclusiveStrategy;
    }

    /**
     * Strateji tipine göre uygun implementasyonu döner.
     *
     * @param type strateji türü
     * @return ilgili strateji instance'ı
     * @throws IllegalArgumentException bilinmeyen tip gelirse
     */
    public TaxCalculationStrategy resolve(TaxStrategyType type) {
        return switch (type) {
            case EXCLUSIVE -> exclusiveStrategy;
            case INCLUSIVE -> inclusiveStrategy;
            case COMPOUND  -> buildDefaultCompoundStrategy();
        };
    }

    /**
     * Varsayılan compound strateji: exclusive → exclusive iki katman.
     * Gerçek ÖTV → KDV senaryosunda katman oranları context'ten gelmelidir.
     */
    private CompoundTaxStrategy buildDefaultCompoundStrategy() {
        return new CompoundTaxStrategy(List.of(
                new CompoundTaxStrategy.TaxLayer(exclusiveStrategy, java.math.BigDecimal.ZERO),
                new CompoundTaxStrategy.TaxLayer(exclusiveStrategy, java.math.BigDecimal.ZERO)
        ));
    }
}

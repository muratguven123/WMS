package com.wms.finance.tax.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Katmanlı (Compound) vergi hesaplama stratejisi.
 *
 * <p>Birden fazla verginin iç içe uygulandığı senaryoları destekler.
 * Her katman, bir önceki katmanın {@code grandTotal}'ı üzerinden hesaplanır.
 *
 * <p><b>Örnek Senaryo (ÖTV → KDV):</b>
 * <pre>
 *   Mal bedeli          : 1.000 TL
 *   1. Katman – ÖTV %25 : 250 TL   → Ara toplam: 1.250 TL
 *   2. Katman – KDV %20 : 250 TL   → Genel toplam: 1.500 TL
 * </pre>
 *
 * <p>Strateji listesi sıralıdır; ilk eleman ilk uygulanan vergidir.
 */
public class CompoundTaxStrategy implements TaxCalculationStrategy {

    private static final int MONETARY_SCALE = 2;

    /**
     * Her katman için (strateji, oran) çiftini tutar.
     */
    public record TaxLayer(TaxCalculationStrategy strategy, BigDecimal rate) {
        public TaxLayer {
            if (strategy == null) throw new IllegalArgumentException("Strateji null olamaz.");
            if (rate == null || rate.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Oran negatif olamaz.");
            }
        }
    }

    private final List<TaxLayer> layers;

    /**
     * @param layers Sıralı vergi katmanları (örn: [ÖTV katmanı, KDV katmanı])
     */
    public CompoundTaxStrategy(List<TaxLayer> layers) {
        if (layers == null || layers.isEmpty()) {
            throw new IllegalArgumentException("En az bir vergi katmanı gereklidir.");
        }
        this.layers = List.copyOf(layers);
    }

    /**
     * Katmanlar sırayla uygulanır. Her katmanda {@code baseAmount} olarak
     * bir önceki katmanın {@code grandTotal}'ı kullanılır.
     *
     * @param baseAmount ilk katman için matrah
     * @param rate       bu parametrenin değeri göz ardı edilir; her katman kendi oranını kullanır
     */
    @Override
    public TaxCalculationResult calculateTax(BigDecimal baseAmount, BigDecimal rate) {
        if (baseAmount == null || baseAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Başlangıç matrahı negatif olamaz.");
        }

        BigDecimal runningBase      = baseAmount;
        BigDecimal cumulativeTax    = BigDecimal.ZERO;
        BigDecimal lastGrandTotal   = baseAmount;

        for (TaxLayer layer : layers) {
            TaxCalculationResult layerResult = layer.strategy().calculateTax(runningBase, layer.rate());
            cumulativeTax  = cumulativeTax.add(layerResult.taxAmount());
            lastGrandTotal = layerResult.grandTotal();
            // Bir sonraki katman, bu katmanın toplamı (ÖTV sonrası bedel) üzerinden çalışır
            runningBase    = layerResult.grandTotal();
        }

        BigDecimal scaledBase  = baseAmount.setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal scaledTax   = cumulativeTax.setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal scaledTotal = scaledBase.add(scaledTax);

        return new TaxCalculationResult(scaledBase, scaledTax, scaledTotal);
    }

    /** Tanımlı vergi katmanlarına salt okunur erişim. */
    public List<TaxLayer> getLayers() {
        return layers;
    }
}

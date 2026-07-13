package com.wms.finance.tax.engine.model;

import java.math.BigDecimal;

/**
 * Engine'den dönen tek bir kalem-kural kombinasyonunun hesaplama sonucu.
 *
 * @param lineId                 kalem ID'si (TaxLineItem.lineId)
 * @param taxTypeCode            uygulanan vergi tipi kodu
 * @param baseAmount             matrah
 * @param taxAmount              hesaplanan vergi tutarı (muafiyet varsa 0.00)
 * @param grandTotal             genel toplam (matrah + vergi)
 * @param inclusive              KDV dahil mi?
 * @param exempt                 muafiyet uygulandı mı?
 * @param exemptionCode          yasal muafiyet kodu (yoksa null)
 * @param strategyType           uygulanan strateji türü
 * @param logId                  oluşturulan TaxCalculationLog'un PK'sı
 */
public record TaxCalculationResultDto(
        Long lineId,
        String taxTypeCode,
        BigDecimal baseAmount,
        BigDecimal taxAmount,
        BigDecimal grandTotal,
        boolean inclusive,
        boolean exempt,
        String exemptionCode,
        TaxStrategyType strategyType,
        Long logId
) {}

package com.wms.finance.dto;

import com.wms.finance.entity.enums.AuditActionType;
import com.wms.finance.entity.enums.RateType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * POST /api/rates/manual — manuel kur işlemi yanıtı.
 *
 * @param id             kaydedilen ExchangeRate Long'si
 * @param sourceCurrency kaynak para birimi kodu
 * @param targetCurrency hedef para birimi kodu
 * @param rateDate       kur tarihi
 * @param rateType       kur türü
 * @param rate           yeni kur değeri
 * @param action         gerçekleştirilen işlem (INSERT veya UPDATE)
 * @param previousRate   güncelleme yapıldıysa eski kur değeri, yeni kayıtta {@code null}
 */
public record ManualRateResponse(
        Long id,
        String sourceCurrency,
        String targetCurrency,
        LocalDate rateDate,
        RateType rateType,
        BigDecimal rate,
        AuditActionType action,
        BigDecimal previousRate
) {}

package com.wms.billing.service;

import com.wms.billing.domain.entity.ExchangeDifferenceLog;
import com.wms.billing.domain.entity.Invoice;
import com.wms.billing.exception.InvoiceNotFoundException;
import com.wms.billing.repository.ExchangeDifferenceLogRepository;
import com.wms.billing.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Kur Farkı Hesaplama ve Loglama Servisi.
 *
 * <h3>Algoritma</h3>
 * <ol>
 *   <li>Faturayı DB'den oku → faturalama anındaki dondurulmuş kuru ({@code exchangeRateValue}) al.</li>
 *   <li>{@code rateDifference = rateAtPayment − invoice.exchangeRateValue}</li>
 *   <li>{@code exchangeDifferenceAmount = paidAmountOriginal × rateDifference} (Banker's Rounding, scale=4)</li>
 *   <li>Sonucu {@link ExchangeDifferenceLog} olarak persist et.</li>
 * </ol>
 *
 * <h3>İşaret Yorumu</h3>
 * <ul>
 *   <li>Pozitif → kur yükseldi, şirket lehine kur kazancı.</li>
 *   <li>Negatif → kur düştü, şirket aleyhine kur kaybı.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeDifferenceService {

    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    private static final int          SCALE     = 4;

    private final InvoiceRepository              invoiceRepository;
    private final ExchangeDifferenceLogRepository logRepository;

    /**
     * Ödeme kuru ile fatura kurunun farkından doğan kur farkını hesaplar ve loglar.
     *
     * @param invoiceId           fatura Long'si
     * @param paidAmountOriginal  ödenen tutar (fatura para birimi cinsinden)
     * @param rateAtPayment       ödeme anındaki gerçek kur (invoiceCurrency → accountingCurrency)
     * @return kalıcı hâle getirilmiş {@link ExchangeDifferenceLog} kaydı
     * @throws InvoiceNotFoundException fatura bulunamazsa
     */
    @Transactional
    public ExchangeDifferenceLog calculateAndLogExchangeDifference(
            Long invoiceId,
            BigDecimal paidAmountOriginal,
            BigDecimal rateAtPayment
    ) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        BigDecimal lockedRate = invoice.getExchangeRateValue();

        // rateDifference = rateAtPayment − lockedRate
        // Pozitif → kur yükseldi (kazanç), Negatif → kur düştü (kayıp)
        BigDecimal rateDifference = rateAtPayment
                .subtract(lockedRate)
                .setScale(SCALE + 2, ROUNDING); // Ara adımda ekstra hassasiyet

        // exchangeDifferenceAmount = paidAmountOriginal × rateDifference
        BigDecimal differenceAmount = paidAmountOriginal
                .multiply(rateDifference)
                .setScale(SCALE, ROUNDING);

        String actionTaken = resolveAction(differenceAmount);

        log.info("Kur farkı hesaplandı: invoiceId={}, lockedRate={}, rateAtPayment={}, " +
                        "paidAmount={}, rateDiff={}, diffAmount={}, action={}",
                invoiceId, lockedRate, rateAtPayment,
                paidAmountOriginal, rateDifference, differenceAmount, actionTaken);

        ExchangeDifferenceLog logEntry = ExchangeDifferenceLog.builder()
                .invoice(invoice)
                .calculationDate(LocalDateTime.now())
                .originalPaidAmount(paidAmountOriginal)
                .rateAtPayment(rateAtPayment.setScale(6, ROUNDING))
                .exchangeDifferenceAmount(differenceAmount)
                .actionTaken(actionTaken)
                .build();

        return logRepository.save(logEntry);
    }

    // -------------------------------------------------------------------------
    // Yardımcı
    // -------------------------------------------------------------------------

    /**
     * Kur farkı tutarının işaretine göre varsayılan aksiyon etiketini döndürür.
     * İş akışında override edilebilir (örn. ERP'ye farklı hesap kodu gönderilmesi).
     */
    private String resolveAction(BigDecimal differenceAmount) {
        int sign = differenceAmount.compareTo(BigDecimal.ZERO);
        if (sign > 0) return "EXCHANGE_GAIN";        // Kur kazancı
        if (sign < 0) return "EXCHANGE_LOSS";        // Kur kaybı
        return "NO_DIFFERENCE";                      // Fark yok
    }
}

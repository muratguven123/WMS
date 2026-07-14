package com.wms.billing.service;

import com.wms.billing.domain.entity.ExchangeDifferenceLog;
import com.wms.billing.domain.entity.Invoice;
import com.wms.billing.exception.InvoiceNotFoundException;
import com.wms.billing.repository.ExchangeDifferenceLogRepository;
import com.wms.billing.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Kur Farkı Hesaplama ve Loglama Servisi.
 * Pozitif kur farkında finance vergi motorundan kur farkı vergisi hesaplanır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeDifferenceService {

    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    private static final int SCALE = 4;

    private final InvoiceRepository invoiceRepository;
    private final ExchangeDifferenceLogRepository logRepository;
    private final TaxLookupService taxLookupService;

    @Value("${billing.exchange-difference-tax-type:KDV}")
    private String exchangeDifferenceTaxType;

    @Value("${billing.default-country-id:1}")
    private Long defaultCountryId;

    @Transactional
    public ExchangeDifferenceLog calculateAndLogExchangeDifference(
            Long invoiceId,
            BigDecimal paidAmountOriginal,
            BigDecimal rateAtPayment
    ) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        BigDecimal lockedRate = invoice.getExchangeRateValue();

        BigDecimal rateDifference = rateAtPayment
                .subtract(lockedRate)
                .setScale(SCALE + 2, ROUNDING);

        BigDecimal differenceAmount = paidAmountOriginal
                .multiply(rateDifference)
                .setScale(SCALE, ROUNDING);

        String actionTaken = resolveAction(differenceAmount);

        BigDecimal taxAmount = null;
        String taxTypeCode = null;
        if (differenceAmount.compareTo(BigDecimal.ZERO) > 0) {
            try {
                TaxLookupService.TaxLineResult taxResult = taxLookupService.calculateExchangeDifferenceTax(
                        differenceAmount,
                        exchangeDifferenceTaxType,
                        LocalDate.now(),
                        invoice.getLocationId(),
                        defaultCountryId);
                taxAmount = taxResult.tax();
                taxTypeCode = taxResult.taxTypeCode() != null
                        ? taxResult.taxTypeCode()
                        : exchangeDifferenceTaxType;
            } catch (Exception ex) {
                log.warn("Kur farkı vergisi hesaplanamadı (invoiceId={}): {}", invoiceId, ex.getMessage());
            }
        }

        log.info("Kur farkı hesaplandı: invoiceId={}, lockedRate={}, rateAtPayment={}, " +
                        "paidAmount={}, rateDiff={}, diffAmount={}, action={}, taxAmount={}",
                invoiceId, lockedRate, rateAtPayment,
                paidAmountOriginal, rateDifference, differenceAmount, actionTaken, taxAmount);

        ExchangeDifferenceLog logEntry = ExchangeDifferenceLog.builder()
                .invoice(invoice)
                .calculationDate(LocalDateTime.now())
                .originalPaidAmount(paidAmountOriginal)
                .rateAtPayment(rateAtPayment.setScale(6, ROUNDING))
                .exchangeDifferenceAmount(differenceAmount)
                .actionTaken(actionTaken)
                .taxAmount(taxAmount)
                .taxTypeCode(taxTypeCode)
                .build();

        return logRepository.save(logEntry);
    }

    private String resolveAction(BigDecimal differenceAmount) {
        int sign = differenceAmount.compareTo(BigDecimal.ZERO);
        if (sign > 0) return "EXCHANGE_GAIN";
        if (sign < 0) return "EXCHANGE_LOSS";
        return "NO_DIFFERENCE";
    }
}

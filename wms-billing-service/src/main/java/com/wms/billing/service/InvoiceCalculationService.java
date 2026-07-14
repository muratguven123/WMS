package com.wms.billing.service;

import com.wms.billing.domain.entity.Invoice;
import com.wms.billing.domain.enums.InvoiceStatus;
import com.wms.billing.domain.enums.RateType;
import com.wms.billing.dto.InvoiceDto;
import com.wms.billing.dto.InvoiceItemInputDto;
import com.wms.billing.dto.InvoiceItemResultDto;
import com.wms.billing.exception.RateLockViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Fatura Hesaplama Motoru — satır vergisi finance Tax Engine üzerinden çözülür
 * (manuel taxRate override desteklenir).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceCalculationService {

    private static final RoundingMode LINE_ROUNDING = RoundingMode.HALF_EVEN;
    private static final int LINE_SCALE = 4;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final CurrencyConversionService currencyConversionService;
    private final TaxLookupService taxLookupService;

    @Value("${billing.accounting-scale:2}")
    private int accountingScale;

    @Value("${billing.accounting-currency:TRY}")
    private String accountingCurrency;

    @Value("${billing.default-country-id:1}")
    private Long defaultCountryId;

    public InvoiceDto calculateInvoice(
            List<InvoiceItemInputDto> items,
            Long customerId,
            Long locationId,
            String invoiceCurrencyCode,
            LocalDate rateDate
    ) {
        return calculateInvoice(items, customerId, locationId, invoiceCurrencyCode, rateDate, null);
    }

    public InvoiceDto calculateInvoice(
            List<InvoiceItemInputDto> items,
            Long customerId,
            Long locationId,
            String invoiceCurrencyCode,
            LocalDate rateDate,
            Long countryId
    ) {
        Long resolvedCountryId = countryId != null ? countryId : defaultCountryId;

        log.debug("Fatura hesaplama başladı: müşteri={}, lokasyon={}, ülke={}, para birimi={}, kur tarihi={}",
                customerId, locationId, resolvedCountryId, invoiceCurrencyCode, rateDate);

        BigDecimal exchangeRateValue = currencyConversionService.getRate(
                invoiceCurrencyCode, accountingCurrency, rateDate, RateType.SELLING);

        List<InvoiceItemResultDto> calculatedItems = items.stream()
                .map(item -> calculateLine(item, customerId, locationId, resolvedCountryId, rateDate))
                .toList();

        BigDecimal subtotalOriginal = calculatedItems.stream()
                .map(InvoiceItemResultDto::lineTotalOriginal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taxAmountOriginal = calculatedItems.stream()
                .map(InvoiceItemResultDto::taxAmountOriginal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grandTotalOriginal = subtotalOriginal.add(taxAmountOriginal)
                .setScale(LINE_SCALE, LINE_ROUNDING);

        BigDecimal grandTotalAccounting = grandTotalOriginal
                .multiply(exchangeRateValue)
                .setScale(accountingScale, LINE_ROUNDING);

        return InvoiceDto.builder()
                .customerId(customerId)
                .locationId(locationId)
                .invoiceCurrency(invoiceCurrencyCode)
                .accountingCurrency(accountingCurrency)
                .exchangeRateDate(rateDate)
                .exchangeRateValue(exchangeRateValue)
                .items(calculatedItems)
                .subtotalOriginal(subtotalOriginal.setScale(LINE_SCALE, LINE_ROUNDING))
                .taxAmountOriginal(taxAmountOriginal.setScale(LINE_SCALE, LINE_ROUNDING))
                .grandTotalOriginal(grandTotalOriginal)
                .grandTotalAccounting(grandTotalAccounting)
                .status(InvoiceStatus.DRAFT)
                .build();
    }

    InvoiceItemResultDto calculateLine(
            InvoiceItemInputDto input,
            Long customerId,
            Long locationId,
            Long countryId,
            LocalDate rateDate) {

        if (input.discountOriginal().compareTo(
                input.quantity().multiply(input.unitPriceOriginal())) > 0) {
            throw new IllegalArgumentException(
                    "İskonto tutarı satır brüt tutarından büyük olamaz: " + input.itemDescription());
        }

        BigDecimal gross = input.quantity()
                .multiply(input.unitPriceOriginal())
                .setScale(LINE_SCALE, LINE_ROUNDING);

        BigDecimal lineTotal = gross
                .subtract(input.discountOriginal())
                .setScale(LINE_SCALE, LINE_ROUNDING);

        BigDecimal taxRate;
        BigDecimal taxAmount;

        if (input.taxRate() != null) {
            taxRate = input.taxRate();
            taxAmount = lineTotal
                    .multiply(taxRate)
                    .divide(HUNDRED, LINE_SCALE, LINE_ROUNDING);
        } else {
            TaxLookupService.TaxLineResult taxResult = taxLookupService.calculateLineTax(
                    lineTotal,
                    input.taxTypeCode(),
                    countryId,
                    locationId,
                    customerId,
                    input.productType(),
                    input.operationType() != null ? input.operationType() : "INVOICE",
                    rateDate);
            taxRate = taxResult.rate() != null ? taxResult.rate() : BigDecimal.ZERO;
            taxAmount = taxResult.tax().setScale(LINE_SCALE, LINE_ROUNDING);
        }

        return InvoiceItemResultDto.builder()
                .itemDescription(input.itemDescription())
                .quantity(input.quantity())
                .unitPriceOriginal(input.unitPriceOriginal())
                .discountOriginal(input.discountOriginal())
                .taxTypeCode(input.taxTypeCode())
                .taxRate(taxRate)
                .lineTotalOriginal(lineTotal)
                .taxAmountOriginal(taxAmount)
                .build();
    }

    public void assertRateLock(Invoice current, BigDecimal incomingRateValue, LocalDate incomingRateDate) {
        if (!isRateLocked(current.getStatus())) {
            return;
        }

        boolean rateChanged = current.getExchangeRateValue().compareTo(incomingRateValue) != 0;
        boolean dateChanged = !current.getExchangeRateDate().equals(incomingRateDate);

        if (rateChanged || dateChanged) {
            log.warn("Rate-lock ihlali tespit edildi: invoiceId={}, status={}",
                    current.getId(), current.getStatus());
            throw new RateLockViolationException(current.getId());
        }
    }

    private boolean isRateLocked(InvoiceStatus status) {
        return status == InvoiceStatus.APPROVED || status == InvoiceStatus.SENT_TO_ERP;
    }
}

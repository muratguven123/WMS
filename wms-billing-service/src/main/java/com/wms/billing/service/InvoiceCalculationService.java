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
 * Fatura Hesaplama Motoru.
 *
 * <h3>Yuvarlama Politikası</h3>
 * <ul>
 *   <li>Satır bazı ara hesaplamalar: {@link RoundingMode#HALF_EVEN} (Banker's Rounding), scale=4.</li>
 *   <li>Muhasebe para birimi çevrimi: {@code accountingScale} (varsayılan 2), aynı yuvarlama modu.</li>
 * </ul>
 *
 * <h3>Kur Locking (Rate Lock)</h3>
 * Faturalama anındaki SELLING kur değeri, {@code exchangeRateValue} alanına kopyalanır.
 * Fatura APPROVED durumuna geçtikten sonra kur bilgisi güncellenemez
 * ({@link #assertRateLock} ile korunur).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceCalculationService {

    // Banker's Rounding — finansal hesaplamalarda sektör standardı
    private static final RoundingMode LINE_ROUNDING       = RoundingMode.HALF_EVEN;
    private static final int           LINE_SCALE          = 4;
    private static final BigDecimal    HUNDRED             = new BigDecimal("100");

    private final CurrencyConversionService currencyConversionService;

    /**
     * Şirketin muhasebe para birimindeki tutar hassasiyeti (varsayılan 2 basamak).
     * application.yml: {@code billing.accounting-scale}
     */
    @Value("${billing.accounting-scale:2}")
    private int accountingScale;

    /**
     * Şirketin muhasebe (yerel) para birimi kodu.
     * application.yml: {@code billing.accounting-currency}
     */
    @Value("${billing.accounting-currency:TRY}")
    private String accountingCurrency;

    // -------------------------------------------------------------------------
    // Ana hesaplama metodu
    // -------------------------------------------------------------------------

    /**
     * Verilen satır kalemleri ve parametrelerden tam bir fatura DTO'su hesaplar.
     *
     * @param items              ham satır kalemleri
     * @param customerId         fatura müşterisi
     * @param locationId         fatura lokasyonu
     * @param invoiceCurrencyCode fatura para birimi (ISO 4217, örn. "EUR")
     * @param rateDate           kurun alınacağı tarih
     * @return hesaplanmış {@link InvoiceDto}
     */
    public InvoiceDto calculateInvoice(
            List<InvoiceItemInputDto> items,
            Long customerId,
            Long locationId,
            String invoiceCurrencyCode,
            LocalDate rateDate
    ) {
        log.debug("Fatura hesaplama başladı: müşteri={}, lokasyon={}, para birimi={}, kur tarihi={}",
                customerId, locationId, invoiceCurrencyCode, rateDate);

        // 1. Kur çözümleme — SELLING tipi, tarih bazlı
        BigDecimal exchangeRateValue = currencyConversionService.getRate(
                invoiceCurrencyCode, accountingCurrency, rateDate, RateType.SELLING);
        log.debug("Kur kilitlendi: {} → {} = {} ({})", invoiceCurrencyCode, accountingCurrency,
                exchangeRateValue, rateDate);

        // 2. Satır hesaplamaları
        List<InvoiceItemResultDto> calculatedItems = items.stream()
                .map(this::calculateLine)
                .toList();

        // 3. Fatura toplamları (orijinal para birimi)
        BigDecimal subtotalOriginal = calculatedItems.stream()
                .map(InvoiceItemResultDto::lineTotalOriginal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taxAmountOriginal = calculatedItems.stream()
                .map(InvoiceItemResultDto::taxAmountOriginal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grandTotalOriginal = subtotalOriginal.add(taxAmountOriginal)
                .setScale(LINE_SCALE, LINE_ROUNDING);

        // 4. Muhasebe para birimine çevrim
        BigDecimal grandTotalAccounting = grandTotalOriginal
                .multiply(exchangeRateValue)
                .setScale(accountingScale, LINE_ROUNDING);

        log.debug("Hesaplama tamamlandı: grandTotalOriginal={} {}, grandTotalAccounting={} {}",
                grandTotalOriginal, invoiceCurrencyCode, grandTotalAccounting, accountingCurrency);

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

    // -------------------------------------------------------------------------
    // Satır hesaplama
    // -------------------------------------------------------------------------

    /**
     * Tek bir satır kaleminin tutarlarını hesaplar.
     *
     * <pre>
     * lineTotalOriginal  = (quantity × unitPriceOriginal) − discountOriginal
     * taxAmountOriginal  = lineTotalOriginal × (taxRate / 100)
     * </pre>
     *
     * Tüm ara adımlar {@link RoundingMode#HALF_EVEN} ile scale=4'e yuvarlanır.
     */
    InvoiceItemResultDto calculateLine(InvoiceItemInputDto input) {
        if (input.discountOriginal().compareTo(
                input.quantity().multiply(input.unitPriceOriginal())) > 0) {
            throw new IllegalArgumentException(
                    "İskonto tutarı satır brüt tutarından büyük olamaz: " + input.itemDescription());
        }

        // gross = quantity × unitPrice
        BigDecimal gross = input.quantity()
                .multiply(input.unitPriceOriginal())
                .setScale(LINE_SCALE, LINE_ROUNDING);

        // lineTotal = gross − discount  (iskonto zaten orijinal para biriminde)
        BigDecimal lineTotal = gross
                .subtract(input.discountOriginal())
                .setScale(LINE_SCALE, LINE_ROUNDING);

        // taxAmount = lineTotal × (taxRate / 100)
        BigDecimal taxAmount = lineTotal
                .multiply(input.taxRate())
                .divide(HUNDRED, LINE_SCALE, LINE_ROUNDING);

        return InvoiceItemResultDto.builder()
                .itemDescription(input.itemDescription())
                .quantity(input.quantity())
                .unitPriceOriginal(input.unitPriceOriginal())
                .discountOriginal(input.discountOriginal())
                .taxRate(input.taxRate())
                .lineTotalOriginal(lineTotal)
                .taxAmountOriginal(taxAmount)
                .build();
    }

    // -------------------------------------------------------------------------
    // Rate-Lock validasyonu
    // -------------------------------------------------------------------------

    /**
     * Onaylanmış faturalarda kur ve kur tarihi alanlarının güncellenmesini engeller.
     *
     * <p>Servis katmanında, fatura güncellemesi öncesinde çağrılır.
     * JPA {@code @PreUpdate} listener yerine servis katmanında uygulanır;
     * böylece business exception mesajı ve stack trace kontrol altında kalır.</p>
     *
     * @param current  veritabanındaki mevcut (kayıtlı) fatura entity'si
     * @param incoming gelen güncelleme isteği (DTO veya entity)
     * @throws RateLockViolationException fatura APPROVED durumundayken kur alanları
     *         değiştirilmeye çalışılırsa fırlatılır
     */
    public void assertRateLock(Invoice current, BigDecimal incomingRateValue, LocalDate incomingRateDate) {
        if (!isRateLocked(current.getStatus())) {
            return;
        }

        // BigDecimal için compareTo kullanılır: equals() scale'e duyarlıdır ve
        // 30.00 ile 30.000000 gibi sayısal olarak özdeş kurları "değişti" sayarak
        // sahte rate-lock ihlali üretirdi.
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

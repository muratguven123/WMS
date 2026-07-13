package com.wms.finance.service;

import com.wms.finance.dto.ExchangeRateDto;
import com.wms.finance.entity.enums.ExchangeDiffPreference;
import com.wms.finance.exception.ExchangeRateNotFoundException;
import com.wms.finance.repository.ExchangeRateRepository;
import com.wms.finance.repository.FinanceCustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Müşteri kartındaki {@code rateType} / {@code rateSource} parametrelerine göre
 * dinamik kur çekimi ve kur farkı hesaplama tercihi yönetimi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerRateService {

    private static final int MAX_FALLBACK_DAYS = 5;

    private final FinanceCustomerRepository customerRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    // ── Kur Sorgulama ────────────────────────────────────────────────────────

    /**
     * Müşterinin kartında tanımlı {@code rateType} ve {@code rateSource} değerlerini
     * kullanarak iki para birimi arasındaki kuru döner.
     *
     * <p>Sorgu önce tam tarih eşleşmesi arar; bulamazsa {@code MAX_FALLBACK_DAYS}
     * geriye giderek en yakın geçmiş kuru kullanır.
     *
     * @param customerId     müşteri kimliği
     * @param sourceCurrency kaynak para birimi kodu (örn. "USD")
     * @param targetCurrency hedef para birimi kodu (örn. "TRY")
     * @param date           işlem tarihi
     * @return müşteriye özgü kur bilgisi
     * @throws IllegalArgumentException      müşteri bulunamazsa
     * @throws ExchangeRateNotFoundException uygun kur kayıt bulunamazsa
     */
    public ExchangeRateDto getCustomerRate(
            Long customerId,
            String sourceCurrency,
            String targetCurrency,
            Instant date) {

        var customer = customerRepository.findByIdWithCurrency(customerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer not found: " + customerId));

        var rateType   = customer.getRateType();
        var rateSource = customer.getRateSource();

        String src = sourceCurrency.trim().toUpperCase();
        String tgt = targetCurrency.trim().toUpperCase();
        LocalDate rateDate = date.atZone(ZoneOffset.UTC).toLocalDate();

        // Tam tarih eşleşmesi dene (müşterinin rateType + rateSource değerleriyle)
        var exact = exchangeRateRepository.findExactRate(
                src, tgt, rateDate, rateType, rateSource);
        if (exact.isPresent()) {
            var er = exact.get();
            return new ExchangeRateDto(src, tgt, er.getRate(), er.getRateDate(),
                    rateType.name(), rateSource.name(), false);
        }

        // Fallback: en yakın geçmiş kur
        LocalDate minDate = rateDate.minusDays(MAX_FALLBACK_DAYS);
        var fallback = exchangeRateRepository
                .findNearestPastRate(
                        src, tgt, rateType.name(), rateSource.name(), rateDate, minDate)
                .orElseThrow(() -> new ExchangeRateNotFoundException(src, tgt, rateType.name()));

        log.warn("Müşteri [{}] için fallback kur kullanıldı: {}/{} type={} requested={} used={}",
                customerId, src, tgt, rateType, rateDate, fallback.getRateDate());

        return new ExchangeRateDto(src, tgt, fallback.getRate(), fallback.getRateDate(),
                rateType.name(), rateSource.name(), true);
    }

    // ── Kur Farkı Tercihi ────────────────────────────────────────────────────

    /**
     * Müşterinin {@code exchangeDiffPreference} ayarına göre kur farkı hesaplamasının
     * bu fatura veya döngü için çalışıp çalışmayacağına karar verir.
     *
     * @param preference müşteri kur farkı tercihi
     * @param context    hangi tetikleyici bağlamından çağrıldığı ({@code PER_INVOICE} veya {@code MONTHLY})
     * @return {@code true} ise kur farkı hesabı çalıştırılmalı
     */
    public boolean shouldCalculateExchangeDiff(ExchangeDiffPreference preference,
                                               ExchangeDiffContext context) {
        return switch (preference) {
            case NONE -> false;
            case PER_INVOICE -> context == ExchangeDiffContext.PER_INVOICE;
            case MONTHLY     -> context == ExchangeDiffContext.MONTHLY;
        };
    }

    /**
     * Kur farkı hesaplamasının tetiklendiği bağlamı temsil eder.
     */
    public enum ExchangeDiffContext {
        /** Her fatura kesildiğinde tetiklenir. */
        PER_INVOICE,
        /** Ay sonunda toplu mahsup döngüsünde tetiklenir. */
        MONTHLY
    }
}

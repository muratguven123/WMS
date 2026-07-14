package com.wms.finance.service;

import com.wms.finance.dto.ActiveRateListDto;
import com.wms.finance.dto.ExchangeRateDto;
import com.wms.finance.entity.Currency;
import com.wms.finance.exception.ExchangeRateNotFoundException;
import com.wms.finance.repository.CurrencyRepository;
import com.wms.finance.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActiveRateQueryService {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final CurrencyRepository currencyRepository;
    private final CurrencyConversionService currencyConversionService;
    private final ExchangeRateRepository exchangeRateRepository;

    /**
     * Her kur lookup ayrı transaction'da çalışır; bulunamayan kurlar atlanır.
     * Tek bir {@code @Transactional} altında nested lookup exception'ı
     * transaction'ı rollback-only yapıp 500'e yol açmasın diye burada TX açılmaz.
     */
    public ActiveRateListDto listActiveRates(String baseCurrency, LocalDate rateDate, String rateType) {
        String base = baseCurrency.trim().toUpperCase();
        LocalDate effectiveDate = rateDate != null ? rateDate : LocalDate.now(ISTANBUL);

        List<ExchangeRateDto> rates = new ArrayList<>();
        for (Currency currency : currencyRepository.findAllByActiveTrue()) {
            String code = currency.getCode();
            if (code.equalsIgnoreCase(base)) {
                continue;
            }
            try {
                rates.add(currencyConversionService.lookupRate(code, base, effectiveDate, rateType));
            } catch (ExchangeRateNotFoundException ex) {
                log.warn("Aktif kur listesinde {} -> {} bulunamadı: {}", code, base, ex.getMessage());
            }
        }

        rates.sort(Comparator.comparing(ExchangeRateDto::sourceCurrency));

        LocalDateTime lastTcmbSyncAt = exchangeRateRepository.findLatestTcmbUpdateAt().orElse(null);

        return new ActiveRateListDto(base, effectiveDate, rateType, rates, lastTcmbSyncAt);
    }
}

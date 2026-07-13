package com.wms.finance.service;

import com.wms.finance.dto.ConversionResultDto;
import com.wms.finance.dto.ExchangeRateDto;
import com.wms.finance.entity.Currency;
import com.wms.finance.entity.ExchangeRate;
import com.wms.finance.entity.enums.RateSource;
import com.wms.finance.entity.enums.RateType;
import com.wms.finance.exception.ExchangeRateNotFoundException;
import com.wms.finance.repository.CurrencyRepository;
import com.wms.finance.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Banker's Rounding (HALF_EVEN) ve geçmiş kur fallback destekli kur çevrim motoru.
 *
 * <p>Efektif kur türleri ({@link RateType#EFFECTIVE_BUYING}, {@link RateType#EFFECTIVE_SELLING})
 * için kayıt bulunamazsa sırasıyla {@link RateType#BUYING} / {@link RateType#SELLING} türüne
 * düşülür (TCMB banknot kurları seed veya manuel veride olmayabilir).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyConversionService {

    private static final int MAX_FALLBACK_DAYS = 5;
    private static final Duration CACHE_TTL = Duration.ofHours(12);
    private static final String CACHE_PREFIX = "rate:";
  /** Genel kur sorgularında kaynak önceliği: manuel override, ardından TCMB referans. */
    private static final List<RateSource> LOOKUP_SOURCE_PRIORITY = List.of(
            RateSource.MANUAL,
            RateSource.TCMB
    );
    /** Doğrudan kur yoksa çapraz kur hesabında kullanılan köprü para birimleri (sıralı). */
    private static final List<String> TRIANGULATION_BASE_CURRENCIES = List.of("TRY", "USD");
    private static final int RATE_SCALE = 6;

    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyRepository currencyRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional(readOnly = true)
    public ConversionResultDto convert(
            BigDecimal amount,
            String sourceCurrency,
            String targetCurrency,
            Instant transactionDate,
            String rateType
    ) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(transactionDate, "transactionDate");

        String source = normalizeCode(sourceCurrency);
        String target = normalizeCode(targetCurrency);
        RateType type = RateType.valueOf(rateType);

        if (source.equals(target)) {
            Currency currency = loadCurrency(target);
            BigDecimal rounded = round(amount, currency.getDecimalPlaces());
            LocalDate date = transactionDate.atZone(ZoneOffset.UTC).toLocalDate();
            return new ConversionResultDto(amount, source, target, BigDecimal.ONE, rounded, date, false);
        }

        LocalDate requestedDate = transactionDate.atZone(ZoneOffset.UTC).toLocalDate();
        RateLookup lookup = resolveRate(source, target, requestedDate, type);

        Currency targetCurrencyEntity = loadCurrency(target);
        BigDecimal converted = round(amount.multiply(lookup.rate()), targetCurrencyEntity.getDecimalPlaces());

        if (lookup.fallback()) {
            log.warn("Alternatif kur kullanıldı: {} -> {} type={} requested={} used={}",
                    source, target, type, requestedDate, lookup.rateDate());
        }

        return new ConversionResultDto(
                amount, source, target, lookup.rate(), converted, lookup.rateDate(), lookup.fallback());
    }

    /**
     * Faturalama ve diğer servisler için kur değeri sorgusu (tutar çevrimi yapmadan).
     */
    @Transactional(readOnly = true)
    public ExchangeRateDto lookupRate(
            String sourceCurrency,
            String targetCurrency,
            LocalDate rateDate,
            String rateType) {

        String source = normalizeCode(sourceCurrency);
        String target = normalizeCode(targetCurrency);
        RateType type = RateType.valueOf(rateType);

        if (source.equals(target)) {
            return new ExchangeRateDto(source, target, BigDecimal.ONE, rateDate, type.name(),
                    RateSource.TCMB.name(), false);
        }

        RateLookup lookup = resolveRate(source, target, rateDate, type);
        return new ExchangeRateDto(source, target, lookup.rate(), lookup.rateDate(),
                type.name(), lookup.rateSource().name(), lookup.fallback());
    }

    private RateLookup resolveRate(String source, String target, LocalDate requestedDate, RateType type) {
        String cacheKey = buildCacheKey(source, target, requestedDate, type);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof RateLookup lookup) {
            return lookup;
        }

        RateLookup lookup = findRateFromDatabase(source, target, requestedDate, type);
        redisTemplate.opsForValue().set(cacheKey, lookup, CACHE_TTL);
        return lookup;
    }

    private RateLookup findRateFromDatabase(String source, String target, LocalDate requestedDate, RateType type) {
        Optional<RateLookup> directOrInverse = resolveLeg(source, target, requestedDate, type);
        if (directOrInverse.isPresent()) {
            return directOrInverse.get();
        }

        for (String bridge : TRIANGULATION_BASE_CURRENCIES) {
            if (bridge.equals(source) || bridge.equals(target)) {
                continue;
            }
            Optional<RateLookup> triangulated = triangulateVia(source, target, bridge, requestedDate, type);
            if (triangulated.isPresent()) {
                log.debug("Çapraz kur hesaplandı ({} üzerinden): {} -> {} date={}",
                        bridge, source, target, requestedDate);
                return triangulated.get();
            }
        }

        // Doğrudan foreign→TRY yoksa USD köprüsü dene (ör. AED→USD × USD→TRY)
        if ("TRY".equals(target) && !"TRY".equals(source)) {
            Optional<RateLookup> viaUsd = triangulateVia(source, target, "USD", requestedDate, type);
            if (viaUsd.isPresent()) {
                log.debug("Çapraz kur hesaplandı (USD üzerinden): {} -> {} date={}",
                        source, target, requestedDate);
                return viaUsd.get();
            }
        }

        throw new ExchangeRateNotFoundException(source, target, type.name());
    }

    /**
     * Tek bacaklı kur çözümü: doğrudan eşleşme, tarih fallback'i, efektif kur düşüşü ve ters kur.
     */
    private Optional<RateLookup> resolveLeg(String from, String to, LocalDate requestedDate, RateType type) {
        return tryFindLegWithTypeFallback(from, to, requestedDate, type)
                .or(() -> tryFindLegWithTypeFallback(to, from, requestedDate, type).map(this::invertLookup));
    }

    private Optional<RateLookup> tryFindLegWithTypeFallback(
            String source, String target, LocalDate requestedDate, RateType type) {

        Optional<RateLookup> found = tryFindRateWithDateFallback(source, target, requestedDate, type, false);
        if (found.isPresent()) {
            return found;
        }

        return resolveEquivalentBaseType(type)
                .flatMap(baseType -> {
                    log.warn("Efektif kur bulunamadı, {} türüne düşülüyor: {} -> {} date={}",
                            baseType, source, target, requestedDate);
                    return tryFindRateWithDateFallback(source, target, requestedDate, baseType, true);
                });
    }

    /**
     * İki bacaklı çapraz kur: source → bridge → target.
     * Örn. SAR/JPY = (SAR/TRY) × (TRY/JPY) = (SAR/TRY) / (JPY/TRY).
     */
    private Optional<RateLookup> triangulateVia(
            String source, String target, String bridge, LocalDate requestedDate, RateType type) {

        Optional<RateLookup> toBridge = resolveLeg(source, bridge, requestedDate, type);
        Optional<RateLookup> fromBridge = resolveLeg(bridge, target, requestedDate, type);
        if (toBridge.isEmpty() || fromBridge.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(combineLegs(toBridge.get(), fromBridge.get(), requestedDate));
    }

    private RateLookup invertLookup(RateLookup leg) {
        BigDecimal inverted = BigDecimal.ONE.divide(leg.rate(), RATE_SCALE * 2, RoundingMode.HALF_EVEN);
        return new RateLookup(inverted, leg.rateDate(), leg.rateSource(), leg.fallback());
    }

    private RateLookup combineLegs(RateLookup leg1, RateLookup leg2, LocalDate requestedDate) {
        BigDecimal combinedRate = leg1.rate()
                .multiply(leg2.rate())
                .setScale(RATE_SCALE, RoundingMode.HALF_EVEN);

        boolean fallback = leg1.fallback() || leg2.fallback()
                || !leg1.rateDate().equals(requestedDate)
                || !leg2.rateDate().equals(requestedDate);

        LocalDate rateDate = leg1.rateDate().isBefore(leg2.rateDate()) ? leg1.rateDate() : leg2.rateDate();
        RateSource rateSource = higherPrioritySource(leg1.rateSource(), leg2.rateSource());

        return new RateLookup(combinedRate, rateDate, rateSource, fallback);
    }

    private RateSource higherPrioritySource(RateSource first, RateSource second) {
        for (RateSource source : LOOKUP_SOURCE_PRIORITY) {
            if (source == first || source == second) {
                return source;
            }
        }
        return first;
    }

    private Optional<RateLookup> tryFindRateWithDateFallback(
            String source, String target, LocalDate requestedDate, RateType type, boolean typeFallback) {

        for (RateSource rateSource : LOOKUP_SOURCE_PRIORITY) {
            Optional<RateLookup> exact = exchangeRateRepository
                    .findExactRate(source, target, requestedDate, type, rateSource)
                    .map(rate -> toLookup(rate, typeFallback));
            if (exact.isPresent()) {
                return exact;
            }
        }

        LocalDate minDate = requestedDate.minusDays(MAX_FALLBACK_DAYS);
        for (RateSource rateSource : LOOKUP_SOURCE_PRIORITY) {
            Optional<RateLookup> fallback = exchangeRateRepository
                    .findNearestPastRate(
                            source, target, type.name(), rateSource.name(), requestedDate, minDate)
                    .map(rate -> toLookup(rate, typeFallback || !rate.getRateDate().equals(requestedDate)));
            if (fallback.isPresent()) {
                return fallback;
            }
        }

        return Optional.empty();
    }

    private Optional<RateType> resolveEquivalentBaseType(RateType type) {
        return switch (type) {
            case EFFECTIVE_BUYING -> Optional.of(RateType.BUYING);
            case EFFECTIVE_SELLING -> Optional.of(RateType.SELLING);
            default -> Optional.empty();
        };
    }

    private RateLookup toLookup(ExchangeRate rate, boolean fallback) {
        return new RateLookup(rate.getRate(), rate.getRateDate(), rate.getRateSource(), fallback);
    }

    private Currency loadCurrency(String code) {
        return currencyRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown currency: " + code));
    }

    private BigDecimal round(BigDecimal value, int decimalPlaces) {
        return value.setScale(decimalPlaces, RoundingMode.HALF_EVEN);
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private String buildCacheKey(String source, String target, LocalDate date, RateType type) {
        return CACHE_PREFIX + source + ":" + target + ":" + date + ":" + type.name();
    }

    private record RateLookup(BigDecimal rate, LocalDate rateDate, RateSource rateSource, boolean fallback) {}
}

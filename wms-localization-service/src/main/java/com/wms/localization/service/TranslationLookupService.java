package com.wms.localization.service;

import com.wms.localization.event.MissingTranslationEvent;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.repository.TranslationKeyRepository;
import com.wms.localization.repository.TranslationValueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Tek anahtar bazlı çeviri sorgulama servisi.
 *
 * getTranslations(locale, module) toplu yükleme içindir (TranslationServiceImpl).
 * Bu sınıf ise bireysel anahtar sorgularını karşılar:
 *   String getLabel(String keyCode, String locale)
 *
 * Arama sırası:
 *  1. İstenen locale + keyCode → DB (Redis cache'in önünde bu sınıf yok,
 *     çünkü toplu cache'i tercih ederiz; ancak raporlama gibi anlık
 *     ihtiyaçlar için direkt DB sorgusu kabul edilebilir).
 *  2. Bulunamazsa → varsayılan dil (isDefault=true) ile dene.
 *  3. O da yoksa → MissingTranslationEvent yayınla.
 *  4. Son çare olarak keyCode'un kendisini döndür (UI kırılmasın).
 *
 * NOT: Bu servis ReportLocalizationHelper tarafından tüketilir.
 * TranslationService.getTranslations() ile karıştırılmamalıdır —
 * o metot toplu cache'li yükleme, bu metot bireysel ve cache'siz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationLookupService {

    private final TranslationValueRepository translationValueRepository;
    private final TranslationKeyRepository   translationKeyRepository;
    private final LanguageRepository         languageRepository;
    private final ApplicationEventPublisher  eventPublisher;

    /**
     * Tek anahtar için çeviri döner.
     *
     * @param keyCode Çeviri anahtarı (örn: "report.stock.column.code")
     * @param locale  ISO 639-1 dil kodu
     * @return Çeviri metni; bulunamazsa keyCode'un kendisi
     */
    @Transactional(readOnly = true)
    public String getLabel(String keyCode, String locale) {
        if (keyCode == null || keyCode.isBlank()) return "";
        String normalizedLocale = locale.toLowerCase(Locale.ROOT);

        // 1. İstenen locale'da ara
        Optional<String> direct = translationValueRepository
                .findByLanguageCodeAndKeyCode(normalizedLocale, keyCode)
                .filter(tv -> tv.getValue() != null && !tv.getValue().isBlank())
                .map(tv -> tv.getValue());

        if (direct.isPresent()) return direct.get();

        // 2. Varsayılan dili bul
        Optional<String> defaultLocaleOpt = languageRepository
                .findFirstByIsDefaultTrueAndIsActiveTrue()
                .map(lang -> lang.getCode());

        if (defaultLocaleOpt.isPresent()) {
            String defaultLocale = defaultLocaleOpt.get();

            // Zaten varsayılan dildeysek tekrar sorgu atmayalım
            if (!defaultLocale.equalsIgnoreCase(normalizedLocale)) {
                Optional<String> fallback = translationValueRepository
                        .findByLanguageCodeAndKeyCode(defaultLocale, keyCode)
                        .filter(tv -> tv.getValue() != null && !tv.getValue().isBlank())
                        .map(tv -> tv.getValue());

                if (fallback.isPresent()) {
                    log.debug("Fallback kullanıldı → keyCode={}, hedef={}, fallback={}",
                              keyCode, normalizedLocale, defaultLocale);
                    return fallback.get();
                }
            }
        }

        // 3. Her iki dilde de yok → audit et, keyCode döndür
        publishMissingEvent(normalizedLocale, keyCode);
        log.warn("Çeviri bulunamadı, keyCode döndürülüyor → keyCode={}, locale={}",
                 keyCode, normalizedLocale);
        return keyCode;
    }

    /**
     * Birden fazla anahtar için toplu çeviri.
     * Rapor başlık satırı gibi sabit sayıda kolon için kullanışlı.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, String> getLabels(java.util.List<String> keyCodes, String locale) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        for (String keyCode : keyCodes) {
            result.put(keyCode, getLabel(keyCode, locale));
        }
        return result;
    }

    // -------------------------------------------------------------------------

    private void publishMissingEvent(String locale, String keyCode) {
        try {
            String module = translationKeyRepository.findByKeyCode(keyCode)
                    .map(k -> k.getModule())
                    .orElse(null);

            eventPublisher.publishEvent(
                    new MissingTranslationEvent(this, locale, keyCode, module));
        } catch (Exception e) {
            // Event yayınlanamasa bile asıl iş akışı etkilenmemeli
            log.error("MissingTranslationEvent yayınlanamadı → {}", e.getMessage());
        }
    }
}

package com.wms.core.dto.ui;

import com.wms.core.entity.enums.FieldBehavior;
import jakarta.validation.constraints.NotNull;


/**
 * Kural ekleme ({@code POST /api/ui/rules}) ve güncelleme
 * ({@code PUT /api/ui/rules/{id}}) işlemleri için ortak request DTO'su.
 *
 * <h3>Öncelik Atama Mantığı</h3>
 * <p>{@code priority} alanı null gönderilirse, servis katmanı bağlam
 * alanlarından hangisinin dolu olduğuna bakarak otomatik atar:</p>
 * <pre>
 *   locationId dolu  → 50
 *   roleId dolu      → 40
 *   companyId dolu   → 30
 *   countryId dolu   → 20
 *   hiçbiri dolu değil → 10 (global kural)
 * </pre>
 *
 * @param screenFieldId              Kuralın bağlanacağı alan Long'si (zorunlu)
 * @param priority                   Öncelik değeri; null → otomatik hesaplanır
 * @param companyId                  Null → tüm şirketler
 * @param countryId                  Null → tüm ülkeler
 * @param locationId                 Null → tüm lokasyonlar
 * @param roleId                     Null → tüm roller
 * @param operationType              Null → tüm operasyon tipleri
 * @param behavior                   Alanın alacağı davranış (zorunlu)
 * @param defaultValue               Önceden dolu gösterilecek değer
 * @param validationRegex            Sunucu tarafı validasyon regex deseni
 * @param validationErrorMessageKey  Regex hatası i18n anahtarı
 */
public record UpsertRuleRequest(

        @NotNull(message = "screenFieldId zorunludur")
        Long screenFieldId,

        Integer priority,

        Long companyId,
        Long countryId,
        Long locationId,
        Long roleId,
        String operationType,

        @NotNull(message = "behavior zorunludur")
        FieldBehavior behavior,

        String defaultValue,
        String validationRegex,
        String validationErrorMessageKey
) {}

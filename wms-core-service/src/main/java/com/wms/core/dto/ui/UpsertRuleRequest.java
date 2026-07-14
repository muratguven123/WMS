package com.wms.core.dto.ui;

import com.wms.core.entity.enums.FieldBehavior;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


/**
 * Kural ekleme ({@code POST /api/ui/rules}) ve güncelleme
 * ({@code PUT /api/ui/rules/{id}}) işlemleri için ortak request DTO'su.
 *
 * <h3>Öncelik Atama Mantığı</h3>
 * <p>{@code priority} alanı null gönderilirse, servis katmanı bağlam
 * alanlarından hangisinin dolu olduğuna bakarak otomatik atar
 * (ilk eşleşen kazanır):</p>
 * <pre>
 *   warehouseId dolu        → 60
 *   locationId dolu         → 50
 *   roleId dolu             → 40
 *   customerType dolu       → 36
 *   productType dolu        → 34
 *   transactionStatus dolu  → 32
 *   companyId dolu          → 30
 *   countryId dolu          → 20
 *   hiçbiri dolu değil      → 10 (global kural)
 * </pre>
 *
 * <p>String boyut kodları servis katmanında normalize edilir
 * (trim + UPPER) ve {@code ^[A-Z][A-Z0-9_]*$} desenine göre doğrulanır.</p>
 *
 * @param screenFieldId              Kuralın bağlanacağı alan Long'si (zorunlu)
 * @param priority                   Öncelik değeri; null → otomatik hesaplanır
 * @param companyId                  Null → tüm şirketler
 * @param countryId                  Null → tüm ülkeler
 * @param locationId                 Null → tüm lokasyonlar
 * @param roleId                     Null → tüm roller
 * @param operationType              Null → tüm operasyon tipleri
 * @param warehouseId                Null → tüm depolar (Location/Zone referansı)
 * @param customerType               Null → tüm müşteri tipleri; örn: RETAIL, WHOLESALE, ECOMMERCE
 * @param productType                Null → tüm ürün tipleri; örn: STANDARD, HAZMAT, COLD_CHAIN
 * @param transactionStatus          Null → tüm işlem durumları; örn: DRAFT, APPROVED, SHIPPED
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

        Long warehouseId,

        @Size(max = 50, message = "customerType en fazla 50 karakter olabilir")
        String customerType,

        @Size(max = 50, message = "productType en fazla 50 karakter olabilir")
        String productType,

        @Size(max = 50, message = "transactionStatus en fazla 50 karakter olabilir")
        String transactionStatus,

        @NotNull(message = "behavior zorunludur")
        FieldBehavior behavior,

        String defaultValue,
        String validationRegex,
        String validationErrorMessageKey
) {}

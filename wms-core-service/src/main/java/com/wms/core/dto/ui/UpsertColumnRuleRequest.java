package com.wms.core.dto.ui;

import com.wms.core.entity.enums.ColumnBehavior;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Kolon davranış kuralı ekleme request DTO'su.
 *
 * <p>Null bağlam boyutu = "herkes/her durum için geçerli". String boyut kodları
 * servis katmanında normalize edilir (trim + UPPER).</p>
 *
 * @param tableColumnDefId  Kuralın bağlanacağı kolon tanımı (zorunlu)
 * @param priority          Null → boyutlara göre otomatik hesaplanır
 * @param roleId            Null → tüm roller
 * @param companyId         Null → tüm şirketler
 * @param warehouseId       Null → tüm depolar
 * @param customerType      Null → tüm müşteri tipleri; örn: RETAIL
 * @param productType       Null → tüm ürün tipleri; örn: HAZMAT
 * @param transactionStatus Null → tüm işlem durumları; örn: DRAFT
 * @param behavior          Kolonun alacağı davranış (zorunlu)
 */
public record UpsertColumnRuleRequest(
        @NotNull Long tableColumnDefId,
        Integer priority,
        Long roleId,
        Long companyId,
        Long warehouseId,
        @Size(max = 50) String customerType,
        @Size(max = 50) String productType,
        @Size(max = 50) String transactionStatus,
        @NotNull ColumnBehavior behavior
) {}

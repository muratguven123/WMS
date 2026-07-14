package com.wms.core.dto.company;

/**
 * Admin firma listesi DTO'su.
 *
 * <p>Pasif kayıtları ve bağlı aktif depo sayısını da taşır —
 * Firma Yönetimi ekranı listesi içindir.</p>
 *
 * @param locationCount aktif lokasyon (depo) sayısı
 */
public record CompanyAdminDto(
        Long    id,
        Long    organizationId,
        String  organizationName,
        String  name,
        String  taxNumber,
        String  taxOffice,
        boolean active,
        long    locationCount
) {}

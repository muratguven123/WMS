package com.wms.core.dto.geo;

/**
 * Admin ülke listesi DTO'su (İş İsteri 18).
 *
 * <p>Read-only {@code CountryDto}'dan farklı olarak pasif kayıtları ve
 * idari birim sayaçlarını da taşır — Ülke Yönetimi ekranı listesi içindir.</p>
 *
 * @param stateCount aktif eyalet sayısı
 * @param cityCount  aktif şehir sayısı
 */
public record CountryAdminDto(
        Long    id,
        String  isoCode,
        String  name,
        boolean active,
        long    stateCount,
        long    cityCount
) {}

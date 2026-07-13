package com.wms.localization.dto;

import lombok.*;

/**
 * Import/Export arasında taşınan tek satır çeviri verisi.
 * Hem Excel hem JSON formatında ortak DTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranslationRowDto {

    /** Nokta-notasyonlu anahtar: "common.buttons.save" */
    private String keyCode;

    /** UI | REPORT | EMAIL | SYSTEM */
    private String module;

    /** Çeviri metni — boş/null olabilir (eksik çeviri anlamına gelir) */
    private String value;
}

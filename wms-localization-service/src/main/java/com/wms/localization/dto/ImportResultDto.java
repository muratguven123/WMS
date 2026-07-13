package com.wms.localization.dto;

import lombok.*;

import java.util.List;

/**
 * Import işlemi sonuç raporu — yöneticiye gösterilir.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportResultDto {

    private String  locale;
    private int     totalRows;
    private int     insertedCount;
    private int     updatedCount;
    private int     skippedCount;     // boş value olan satırlar

    /** Satır bazında hata listesi (max 100 gösterilir) */
    private List<String> errors;

    private boolean cacheEvicted;
}

package com.wms.localization.dto.notification;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Bildirim şablonlarının aktif dillerdeki kapsama raporu DTO'su.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateCoverageReportDto {
    /** Aktif dillerin tüm aktif şablonlara göre kapsama oranları (örn: tr -> 1.0, en -> 1.0, de -> 0.5) */
    private Map<String, Double> coverageRatio;

    /** Her şablon kodu için eksik olan aktif diller listesi (örn: RECEIPT_APPROVED_MAIL -> ["de"]) */
    private Map<String, List<String>> missingLanguagesPerTemplate;
}

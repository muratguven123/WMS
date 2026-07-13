package com.wms.localization.controller;

import com.wms.localization.dto.ActiveFormatResponse;
import com.wms.localization.security.TenantContext;
import com.wms.localization.security.TenantContextFilter;
import com.wms.localization.security.TenantContextHolder;
import com.wms.localization.service.FormatConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * Format Konfigürasyon REST API.
 *
 * <p>Frontend ve mobil istemcilerin aktif depo lokasyonuna ait tarih/saat/sayı
 * format kurallarını almasını sağlar. İstemci bu bilgiyle:</p>
 * <ul>
 *   <li>Tarih seçici (datepicker) bileşeninin format desenini ayarlar.</li>
 *   <li>Sayı/tutar girişleri için input maskesi uygular.</li>
 *   <li>Görüntüleme için moment.js / date-fns / Intl.DateTimeFormat'a desen map'ler.</li>
 * </ul>
 *
 * <h3>Güvenlik</h3>
 * <p>Endpoint, kimlik doğrulaması gerektiren uç noktalara standart Spring Security
 * kurallarıyla korunur. {@code TenantContextFilter} her istekte {@code TenantContextHolder}'ı
 * doldurur; bu endpoint onu kullanarak doğrudan aktif depoyu bulur.</p>
 *
 * <h3>Örnek İstek / Yanıt</h3>
 * <pre>
 * GET /api/v1/formats/active
 * Authorization: Bearer &lt;token&gt;
 *
 * 200 OK
 * {
 *   "dateFormat":        "dd.MM.yyyy",
 *   "timeFormat":        "HH:mm",
 *   "decimalSeparator":  ",",
 *   "thousandSeparator": "."
 * }
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping({"/api/formats", "/api/v1/formats"})
@RequiredArgsConstructor
public class FormatConfigController {

    private final FormatConfigService formatConfigService;

    /**
     * Aktif deponun format konfigürasyonunu döner.
     *
     * <p>Çözümleme öncelik sırası:</p>
     * <ol>
     *   <li>Depoya özel {@code LocationFormatOverride} kaydı</li>
     *   <li>Ülke varsayılanı {@code CountryFormatConfig}</li>
     *   <li>Uygulama sabit varsayılanları (ISO 8601)</li>
     * </ol>
     *
     * <p>Bu endpoint asla 404 veya 500 dönmez — en kötü durumda uygulama
     * varsayılanları ile 200 döner.</p>
     *
     * @return {@link ActiveFormatResponse} — tarih, saat ve sayı format bilgileri
     */
    @GetMapping("/active")
    public ResponseEntity<ActiveFormatResponse> getActiveFormat(HttpServletRequest request) {
        Long locationId = TenantContextHolder.getContext()
                .map(TenantContext::locationId)
                .orElseGet(() -> parseLocationHeader(request));

        log.debug("Format konfigürasyonu istendi. locationId={}", locationId);

        ActiveFormatResponse response = formatConfigService.resolveActiveFormat(locationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Bu endpoint tenant filtresinden muaf olduğundan {@link TenantContextHolder}
     * dolu olmayabilir; UI yine de seçili depoyu header ile gönderir.
     */
    private static Long parseLocationHeader(HttpServletRequest request) {
        String value = request.getHeader(TenantContextFilter.HEADER_LOCATION_ID);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
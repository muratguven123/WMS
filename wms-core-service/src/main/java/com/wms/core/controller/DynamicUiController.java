package com.wms.core.controller;

import com.wms.core.dto.ui.ResolvedScreenDto;
import com.wms.core.dto.ui.ResolvedTableSchemaDto;
import com.wms.core.dto.ui.TablePreferenceRequest;
import com.wms.core.dto.ui.UiContext;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.service.DynamicUiService;
import com.wms.core.service.DynamicTableUiService;
import com.wms.core.service.UiContextFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * Dinamik form şeması REST API'si.
 *
 * <p>Frontend, bir formu yüklemeden önce bu endpoint'i çağırarak
 * hangi alanların görünür/zorunlu/salt okunur olduğunu öğrenir ve
 * formu dinamik olarak çizer.</p>
 *
 * <h3>Örnek İstek</h3>
 * <pre>
 * GET /api/ui/screens/REC_CONTROL_FORM/schema
 *     ?roleId=550e8400-e29b-41d4-a716-446655440000
 *     &countryId=550e8400-e29b-41d4-a716-446655440001
 *     &operationType=CREATE
 * </pre>
 *
 * <p>{@code locationId} ve {@code companyId} TenantContext'ten otomatik alınır
 * (JWT/filter zinciri tarafından set edilmiş olmalı). Frontend'den ek olarak
 * {@code roleId}, {@code countryId} ve {@code operationType} query param ile iletilir.</p>
 */
@RestController
@RequestMapping("/api/ui/screens")
@RequiredArgsConstructor
public class DynamicUiController {

    private final DynamicUiService dynamicUiService;
    private final DynamicTableUiService dynamicTableUiService;
    private final UiContextFactory uiContextFactory;

    /**
     * Belirtilen ekranın, mevcut kullanıcı bağlamında çözümlenmiş form şemasını döner.
     *
     * @param screenCode    yol parametresi — ekran kodu, örn: {@code REC_CONTROL_FORM}
     * @param roleId        kullanıcının aktif rol Long'si (opsiyonel)
     * @param countryId     ülke Long'si — vergi/adres kuralları için (opsiyonel)
     * @param operationType operasyon tipi — {@code CREATE | EDIT | VIEW} (opsiyonel)
     * @return çözümlenmiş form şeması
     */
    @GetMapping("/{screenCode}/schema")
    public ResponseEntity<ResolvedScreenDto> getSchema(
            @PathVariable String screenCode,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Long countryId,
            @RequestParam(required = false) String operationType) {

        // locationId ve companyId TenantContext'ten alınır —
        // bu değerler JWT doğrulama filtresinde set edilmiş olmalı.
        UiContext context = uiContextFactory.fromTenant(
                TenantContextHolder.getLocationId(),
                TenantContextHolder.getCompanyId(),
                roleId,
                countryId,
                operationType
        );

        ResolvedScreenDto schema = dynamicUiService.getResolvedScreen(screenCode, context);
        return ResponseEntity.ok(schema);
    }

    /**
     * Liste ekranı için çözümlenmiş tablo kolon şemasını döner (İş İsteri 16).
     */
    @GetMapping("/{screenCode}/table-schema")
    public ResponseEntity<ResolvedTableSchemaDto> getTableSchema(
            @PathVariable String screenCode,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Long countryId) {

        UiContext context = uiContextFactory.fromTenant(
                TenantContextHolder.getLocationId(),
                TenantContextHolder.getContext().map(c -> c.companyId()).orElse(null),
                roleId,
                countryId,
                null
        );
        return ResponseEntity.ok(dynamicTableUiService.getResolvedTableSchema(screenCode, context));
    }

    /** Kullanıcının tablo kolon tercihini kaydeder. */
    @PutMapping("/{screenCode}/table-preferences")
    public ResponseEntity<Void> saveTablePreferences(
            @PathVariable String screenCode,
            @Valid @RequestBody TablePreferenceRequest request) {
        dynamicTableUiService.savePreferences(screenCode, request);
        return ResponseEntity.ok().build();
    }

    /** Kullanıcı tercihini siler — varsayılana döner. */
    @DeleteMapping("/{screenCode}/table-preferences")
    public ResponseEntity<Void> deleteTablePreferences(@PathVariable String screenCode) {
        dynamicTableUiService.deletePreferences(screenCode);
        return ResponseEntity.noContent().build();
    }
}

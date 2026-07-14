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
 *     ?roleId=42
 *     &countryId=1
 *     &operationType=CREATE
 *     &warehouseId=7
 *     &customerType=RETAIL
 *     &productType=COLD_CHAIN
 *     &transactionStatus=DRAFT
 * </pre>
 *
 * <p>{@code locationId} ve {@code companyId} TenantContext'ten otomatik alınır
 * (JWT/filter zinciri tarafından set edilmiş olmalı). Frontend'den ek olarak
 * {@code roleId}, {@code countryId}, {@code operationType} ve İş İsteri 2.1
 * (Madde 8.3) boyutları — {@code warehouseId}, {@code customerType},
 * {@code productType}, {@code transactionStatus} — query param ile iletilir.</p>
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
     * @param screenCode        yol parametresi — ekran kodu, örn: {@code REC_CONTROL_FORM}
     * @param roleId            kullanıcının aktif rol Long'si (opsiyonel)
     * @param countryId         ülke Long'si — vergi/adres kuralları için (opsiyonel)
     * @param operationType     operasyon tipi — {@code CREATE | EDIT | VIEW} (opsiyonel)
     * @param warehouseId       depo Long'si (opsiyonel)
     * @param customerType      müşteri tipi — örn: {@code RETAIL} (opsiyonel)
     * @param productType       ürün tipi — örn: {@code HAZMAT} (opsiyonel)
     * @param transactionStatus işlem durumu — örn: {@code DRAFT} (opsiyonel)
     * @return çözümlenmiş form şeması
     */
    @GetMapping("/{screenCode}/schema")
    public ResponseEntity<ResolvedScreenDto> getSchema(
            @PathVariable String screenCode,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Long countryId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String transactionStatus) {

        // locationId ve companyId TenantContext'ten alınır —
        // bu değerler JWT doğrulama filtresinde set edilmiş olmalı.
        UiContext context = uiContextFactory.fromTenant(
                TenantContextHolder.getLocationId(),
                TenantContextHolder.getCompanyId(),
                roleId,
                countryId,
                operationType,
                warehouseId,
                customerType,
                productType,
                transactionStatus
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
            @RequestParam(required = false) Long countryId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String transactionStatus) {

        UiContext context = uiContextFactory.fromTenant(
                TenantContextHolder.getLocationId(),
                TenantContextHolder.getContext().map(c -> c.companyId()).orElse(null),
                roleId,
                countryId,
                null,
                warehouseId,
                customerType,
                productType,
                transactionStatus
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

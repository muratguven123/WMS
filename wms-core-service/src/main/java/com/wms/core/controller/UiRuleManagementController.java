package com.wms.core.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.core.dto.ui.RuleResponse;
import com.wms.core.dto.ui.UpsertRuleRequest;
import com.wms.core.service.UiRuleManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * Ekran alan davranış kuralları yönetim API'si.
 *
 * <p>Admin paneli bu endpoint'ler aracılığıyla kural ekler, günceller ve siler.
 * Her write işlemi sonrası Redis cache otomatik temizlenir ve değişiklik
 * audit log'a yazılır.</p>
 *
 * <h3>Endpoint Özeti</h3>
 * <pre>
 * POST   /api/ui/rules           → Yeni kural ekle
 * PUT    /api/ui/rules/{id}      → Mevcut kuralı güncelle
 * DELETE /api/ui/rules/{id}      → Kuralı sil
 * </pre>
 */
@RestController
@RequestMapping("/api/ui/rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WMS_ADMIN')")
public class UiRuleManagementController {

    private final UiRuleManagementService ruleManagementService;

    /**
     * Yeni bir alan davranış kuralı ekler.
     *
     * <p>{@code priority} null gönderilirse servis katmanı bağlam alanlarından
     * otomatik hesaplar (Lokasyon=50, Rol=40, Şirket=30, Ülke=20, Global=10).</p>
     *
     * @param request kural parametreleri
     * @return HTTP 201 + oluşturulan kuralın DTO'su
     */
    @PostMapping
    public ResponseEntity<RuleResponse> createRule(@Valid @RequestBody UpsertRuleRequest request) {
        RuleResponse response = ruleManagementService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Mevcut bir kuralı günceller.
     *
     * <p>Sadece değişen alanlar audit log'a yazılır. Cache ilgili ekran için
     * tamamen temizlenir.</p>
     *
     * @param ruleId  güncellenecek kural Long'si
     * @param request yeni değerler
     * @return HTTP 200 + güncellenmiş kuralın DTO'su
     */
    @PutMapping("/{ruleId}")
    public ResponseEntity<RuleResponse> updateRule(
            @PathVariable Long ruleId,
            @Valid @RequestBody UpsertRuleRequest request) {

        RuleResponse response = ruleManagementService.updateRule(ruleId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Bir kuralı kalıcı olarak siler (hard-delete).
     *
     * <p>Silme işlemi audit log'a yazılır ve cache temizlenir.</p>
     *
     * @param ruleId silinecek kural Long'si
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long ruleId) {
        ruleManagementService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }
}

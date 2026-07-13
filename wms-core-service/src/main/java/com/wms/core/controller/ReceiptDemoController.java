package com.wms.core.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.core.aspect.annotation.ValidateDynamicForm;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Dinamik form validasyonunun demo endpoint'i.
 *
 * <p>{@link ValidateDynamicForm} annotation'ı ile sunucu tarafı kural doğrulaması
 * gösterilir. Gerçek mal kabul akışı ileride ayrı bir servisle değiştirilebilir.</p>
 */
@RestController
@RequestMapping("/api/demo/receipts")
@PreAuthorize("hasRole('WMS_ADMIN')")
public class ReceiptDemoController {

    /**
     * Mal kabul kontrol formu gönderimi — REC_CONTROL_FORM kurallarına göre doğrulanır.
     *
     * <p>Query param olarak {@code countryId}, {@code roleId}, {@code operationType}
     * iletilirse schema API ile aynı bağlam kullanılır.</p>
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> submit(
            @ValidateDynamicForm(screenCode = "REC_CONTROL_FORM")
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "ACCEPTED", "screenCode", "REC_CONTROL_FORM"));
    }
}

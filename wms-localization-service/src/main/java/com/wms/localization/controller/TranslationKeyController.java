package com.wms.localization.controller;

import com.wms.localization.dto.SyncTranslationKeyRequest;
import com.wms.localization.service.TranslationKeySyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/translation-keys")
@RequiredArgsConstructor
public class TranslationKeyController {

    private final TranslationKeySyncService syncService;

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncKeys(
            @Valid @RequestBody List<SyncTranslationKeyRequest> requests) {
        int count = syncService.syncKeys(requests);
        return ResponseEntity.ok(Map.of("syncedKeys", count, "async", true));
    }
}

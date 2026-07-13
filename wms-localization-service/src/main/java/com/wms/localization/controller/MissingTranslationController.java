package com.wms.localization.controller;

import com.wms.localization.service.MissingTranslationReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/translations")
@RequiredArgsConstructor
public class MissingTranslationController {

    private final MissingTranslationReportService reportService;

    @PostMapping("/missing")
    public ResponseEntity<Void> reportMissing(
            @RequestParam String locale,
            @RequestBody List<String> keyCodes) {
        reportService.reportBatch(locale, keyCodes);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/missing")
    public ResponseEntity<Map<String, Object>> listMissing(
            @RequestParam(required = false) String locale,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(reportService.listUnresolved(locale, page, size));
    }
}

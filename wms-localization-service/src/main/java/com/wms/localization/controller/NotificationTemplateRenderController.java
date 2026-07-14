package com.wms.localization.controller;

import com.wms.localization.dto.notification.RenderTemplateRequest;
import com.wms.localization.dto.notification.RenderedTemplateDto;
import com.wms.localization.service.notification.NotificationTemplateRenderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Mikroservisler arası (internal) bildirim şablonu render API'si.
 * Keycloak client credentials ile yetkilendirilmiş istekleri kabul eder.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notification-templates")
@RequiredArgsConstructor
@Validated
public class NotificationTemplateRenderController {

    private final NotificationTemplateRenderService renderService;

    @PostMapping("/render")
    @PreAuthorize("hasAnyRole('SYSTEM', 'LOCALIZATION_ADMIN', 'WMS_ADMIN', 'NOTIFICATION_SERVICE')")
    public ResponseEntity<RenderedTemplateDto> render(
            @Valid @RequestBody RenderTemplateRequest request) {
        log.debug("Internal notification template render requested → code={}, lang={}",
                request.getTemplateCode(), request.getLanguageCode());

        RenderedTemplateDto rendered = renderService.renderInternal(
                request.getTemplateCode(),
                request.getLanguageCode(),
                request.getVariables(),
                request.getStrategy(),
                true // onlyActive = true
        );

        return ResponseEntity.ok(rendered);
    }
}

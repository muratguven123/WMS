package com.wms.localization.service;

import com.wms.localization.entity.MissingTranslationLog;
import com.wms.localization.event.MissingTranslationEvent;
import com.wms.localization.repository.MissingTranslationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MissingTranslationReportService {

    private final MissingTranslationLogRepository logRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void reportBatch(String locale, List<String> keyCodes) {
        if (locale == null || locale.isBlank() || keyCodes == null) {
            return;
        }
        for (String keyCode : keyCodes) {
            if (keyCode == null || keyCode.isBlank()) {
                continue;
            }
            eventPublisher.publishEvent(
                    new MissingTranslationEvent(this, locale.toLowerCase(), keyCode, "UI"));
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listUnresolved(String locale, int page, int size) {
        Page<MissingTranslationLog> result = locale == null || locale.isBlank()
                ? logRepository.findByResolvedFalseOrderByHitCountDesc(PageRequest.of(page, size))
                : logRepository.findByLocaleAndResolvedFalseOrderByHitCountDesc(
                        locale.toLowerCase(), PageRequest.of(page, size));

        return Map.of(
                "items", result.getContent(),
                "total", result.getTotalElements(),
                "page", page,
                "unresolvedCount", logRepository.countByResolvedFalse());
    }
}

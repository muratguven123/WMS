package com.wms.core.service;

import com.wms.core.dto.ui.RuleResponse;
import com.wms.core.dto.ui.UpsertRuleRequest;
import com.wms.core.entity.FieldBehaviorRule;
import com.wms.core.entity.ScreenField;
import com.wms.core.event.ConfigChangeEvent;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.FieldBehaviorRuleRepository;
import com.wms.core.repository.ScreenFieldRepository;
import com.wms.core.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Ekran alanı kural yönetim servisi.
 *
 * <h3>Sorumluluklar</h3>
 * <ol>
 *   <li><b>Kural ekleme/güncelleme:</b> Öncelik otomatik hesaplama, çakışma kontrolü,
 *       DB kaydı.</li>
 *   <li><b>Cache temizleme:</b> Her write işlemi sonrası ilgili ekrana ait Redis
 *       cache'ini siler; {@link DynamicUiService#evictScreenCache(String)} kullanılır.</li>
 *   <li><b>Audit loglama:</b> {@link ConfigChangeEvent} yayınlar;
 *       {@link AuditLogService} async olarak DB'ye yazar.</li>
 * </ol>
 *
 * <h3>Öncelik Hiyerarşisi (otomatik atama)</h3>
 * <pre>
 *   locationId dolu  → 50
 *   roleId dolu      → 40
 *   companyId dolu   → 30
 *   countryId dolu   → 20
 *   global (hepsi null) → 10
 * </pre>
 *
 * <p>El ile girilen {@code priority} bu hesaplamayı geçersiz kılar.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UiRuleManagementService {

    // Varsayılan öncelik sabitleri
    private static final int PRIORITY_LOCATION = 50;
    private static final int PRIORITY_ROLE      = 40;
    private static final int PRIORITY_COMPANY   = 30;
    private static final int PRIORITY_COUNTRY   = 20;
    private static final int PRIORITY_GLOBAL    = 10;

    private final FieldBehaviorRuleRepository ruleRepository;
    private final ScreenFieldRepository       screenFieldRepository;
    private final DynamicUiService            dynamicUiService;
    private final ApplicationEventPublisher   eventPublisher;

    // ── Kural Ekleme ──────────────────────────────────────────────────────────

    /**
     * Yeni bir davranış kuralı ekler.
     *
     * <p>Akış:</p>
     * <ol>
     *   <li>Alan varlığını doğrula.</li>
     *   <li>Öncelik hesapla (null ise otomatik; değilse el ile değeri kullan).</li>
     *   <li>Çakışma kontrolü (aynı alan + aynı priority).</li>
     *   <li>DB'ye kaydet.</li>
     *   <li>Cache evict → Audit log yayınla.</li>
     * </ol>
     *
     * @param request kural parametreleri
     * @return kaydedilen kuralın response DTO'su
     */
    public RuleResponse createRule(UpsertRuleRequest request) {
        ScreenField field = findField(request.screenFieldId());

        int priority = resolvePriority(request);
        checkConflict(request.screenFieldId(), priority, null);

        FieldBehaviorRule rule = buildRule(field, request, priority);
        ruleRepository.save(rule);

        log.info("[UiRule] Kural eklendi. ruleId={} fieldKey={} behavior={} priority={}",
                rule.getId(), field.getFieldKey(), rule.getBehavior(), priority);

        evictAndAudit(rule, field, "CREATE",
                buildCreateChanges(rule),
                TenantContextHolder.getUserId());

        return RuleResponse.from(rule);
    }

    // ── Kural Güncelleme ──────────────────────────────────────────────────────

    /**
     * Mevcut bir kuralı günceller.
     *
     * <p>Değişen alanlar diff olarak hesaplanır; her değişen alan için
     * ayrı {@link ConfigChangeEvent.FieldChange} kaydı oluşturulur.</p>
     *
     * @param ruleId  güncellenecek kural Long'si
     * @param request yeni değerler
     * @return güncellenmiş kuralın response DTO'su
     */
    public RuleResponse updateRule(Long ruleId, UpsertRuleRequest request) {
        FieldBehaviorRule rule = findRule(ruleId);
        ScreenField field = rule.getScreenField();

        int newPriority = resolvePriority(request);

        // Öncelik değiştiyse çakışma kontrolü yap
        if (newPriority != rule.getPriority()) {
            checkConflict(field.getId(), newPriority, ruleId);
        }

        // Diff hesapla (audit log için)
        List<ConfigChangeEvent.FieldChange> changes = buildUpdateChanges(rule, request, newPriority);

        // Alanları güncelle
        applyUpdate(rule, request, newPriority);
        ruleRepository.save(rule);

        log.info("[UiRule] Kural güncellendi. ruleId={} fieldKey={} değişen={}",
                ruleId, field.getFieldKey(), changes.stream()
                        .map(ConfigChangeEvent.FieldChange::fieldName).toList());

        if (!changes.isEmpty()) {
            evictAndAudit(rule, field, "UPDATE", changes, TenantContextHolder.getUserId());
        }

        return RuleResponse.from(rule);
    }

    // ── Kural Silme ───────────────────────────────────────────────────────────

    /**
     * Bir kuralı kalıcı olarak siler (hard-delete) ve cache'i temizler.
     *
     * @param ruleId silinecek kural Long'si
     */
    public void deleteRule(Long ruleId) {
        FieldBehaviorRule rule = findRule(ruleId);
        ScreenField field = rule.getScreenField();

        ruleRepository.delete(rule);

        log.info("[UiRule] Kural silindi. ruleId={} fieldKey={} behavior={}",
                ruleId, field.getFieldKey(), rule.getBehavior());

        // Audit log — silme tek satırlık değişim
        List<ConfigChangeEvent.FieldChange> changes = List.of(
                new ConfigChangeEvent.FieldChange("behavior", rule.getBehavior().name(), null)
        );
        evictAndAudit(rule, field, "DELETE", changes, TenantContextHolder.getUserId());
    }

    // ── Yardımcı: Öncelik Hesaplama ───────────────────────────────────────────

    /**
     * Request'teki {@code priority} null ise bağlam alanlarından otomatik hesaplar,
     * dolu ise el ile girilen değeri döner.
     *
     * <p>Hiyerarşi (ilk eşleşen kazanır): lokasyon → rol → şirket → ülke → global</p>
     */
    int resolvePriority(UpsertRuleRequest request) {
        if (request.priority() != null) {
            return request.priority();
        }
        if (request.locationId() != null) return PRIORITY_LOCATION;
        if (request.roleId()     != null) return PRIORITY_ROLE;
        if (request.companyId()  != null) return PRIORITY_COMPANY;
        if (request.countryId()  != null) return PRIORITY_COUNTRY;
        return PRIORITY_GLOBAL;
    }

    // ── Yardımcı: Çakışma Kontrolü ───────────────────────────────────────────

    /**
     * Aynı alan + aynı priority kombinasyonunda başka kural var mı kontrol eder.
     *
     * @param screenFieldId alan Long'si
     * @param priority      kontrol edilecek öncelik
     * @param excludeId     güncelleme senaryosunda mevcut kuralı hariç tut; null → yok
     */
    private void checkConflict(Long screenFieldId, int priority, Long excludeId) {
        if (ruleRepository.existsConflict(screenFieldId, priority, excludeId)) {
            throw new BusinessException(
                    "Bu alan için priority=%d değerinde zaten bir kural mevcut. "
                    + "Farklı bir öncelik değeri girin veya mevcut kuralı güncelleyin."
                    .formatted(priority),
                    HttpStatus.CONFLICT,
                    "UI_RULE_PRIORITY_CONFLICT");
        }
    }

    // ── Yardımcı: Entity Oluşturma / Güncelleme ───────────────────────────────

    private FieldBehaviorRule buildRule(ScreenField field, UpsertRuleRequest request, int priority) {
        return FieldBehaviorRule.builder()
                .screenField(field)
                .priority(priority)
                .companyId(request.companyId())
                .countryId(request.countryId())
                .locationId(request.locationId())
                .roleId(request.roleId())
                .operationType(request.operationType())
                .behavior(request.behavior())
                .defaultValue(request.defaultValue())
                .validationRegex(request.validationRegex())
                .validationErrorMessageKey(request.validationErrorMessageKey())
                .build();
    }

    private void applyUpdate(FieldBehaviorRule rule, UpsertRuleRequest request, int newPriority) {
        rule.setPriority(newPriority);
        rule.setCompanyId(request.companyId());
        rule.setCountryId(request.countryId());
        rule.setLocationId(request.locationId());
        rule.setRoleId(request.roleId());
        rule.setOperationType(request.operationType());
        rule.setBehavior(request.behavior());
        rule.setDefaultValue(request.defaultValue());
        rule.setValidationRegex(request.validationRegex());
        rule.setValidationErrorMessageKey(request.validationErrorMessageKey());
    }

    // ── Yardımcı: Audit Diff Hesaplama ───────────────────────────────────────

    /**
     * Yeni kural eklenirken tüm dolu alanları "null → değer" olarak loglar.
     */
    private List<ConfigChangeEvent.FieldChange> buildCreateChanges(FieldBehaviorRule rule) {
        List<ConfigChangeEvent.FieldChange> changes = new ArrayList<>();
        changes.add(change("behavior", null, rule.getBehavior().name()));
        changes.add(change("priority", null, String.valueOf(rule.getPriority())));
        if (rule.getLocationId()   != null) changes.add(change("locationId",  null, rule.getLocationId().toString()));
        if (rule.getRoleId()       != null) changes.add(change("roleId",      null, rule.getRoleId().toString()));
        if (rule.getCompanyId()    != null) changes.add(change("companyId",   null, rule.getCompanyId().toString()));
        if (rule.getCountryId()    != null) changes.add(change("countryId",   null, rule.getCountryId().toString()));
        if (rule.getValidationRegex() != null) changes.add(change("validationRegex", null, rule.getValidationRegex()));
        return changes;
    }

    /**
     * Güncelleme öncesi ve sonrasını karşılaştırarak sadece değişen alanları döner.
     */
    private List<ConfigChangeEvent.FieldChange> buildUpdateChanges(FieldBehaviorRule existing,
                                                                    UpsertRuleRequest request,
                                                                    int newPriority) {
        List<ConfigChangeEvent.FieldChange> changes = new ArrayList<>();

        if (!existing.getBehavior().equals(request.behavior())) {
            changes.add(change("behavior", existing.getBehavior().name(), request.behavior().name()));
        }
        if (existing.getPriority() != newPriority) {
            changes.add(change("priority",
                    String.valueOf(existing.getPriority()),
                    String.valueOf(newPriority)));
        }
        if (!eqNullable(existing.getValidationRegex(), request.validationRegex())) {
            changes.add(change("validationRegex", existing.getValidationRegex(), request.validationRegex()));
        }
        if (!eqNullable(existing.getDefaultValue(), request.defaultValue())) {
            changes.add(change("defaultValue", existing.getDefaultValue(), request.defaultValue()));
        }
        if (!eqNullable(uuidStr(existing.getLocationId()), uuidStr(request.locationId()))) {
            changes.add(change("locationId", uuidStr(existing.getLocationId()), uuidStr(request.locationId())));
        }
        if (!eqNullable(uuidStr(existing.getRoleId()), uuidStr(request.roleId()))) {
            changes.add(change("roleId", uuidStr(existing.getRoleId()), uuidStr(request.roleId())));
        }

        return changes;
    }

    // ── Yardımcı: Cache + Audit ───────────────────────────────────────────────

    /**
     * Ekrana ait Redis cache'ini temizler ve {@link ConfigChangeEvent} yayınlar.
     *
     * <p>Cache eviction önce yapılır — böylece yayın sırasında hata olsa bile
     * cache'de stale veri kalmaz.</p>
     */
    private void evictAndAudit(FieldBehaviorRule rule,
                                ScreenField field,
                                String actionType,
                                List<ConfigChangeEvent.FieldChange> changes,
                                Long changedByUserId) {
        // 1. Cache temizle
        String screenCode = field.getScreen().getCode();
        dynamicUiService.evictScreenCache(screenCode);

        // 2. Audit log yayınla (async — AuditLogService @Async ile işler)
        if (!changes.isEmpty()) {
            eventPublisher.publishEvent(new ConfigChangeEvent(
                    this,
                    FieldBehaviorRule.class.getSimpleName(),
                    rule.getId(),
                    actionType,
                    changes,
                    changedByUserId
            ));
        }
    }

    // ── Lookup Yardımcıları ───────────────────────────────────────────────────

    private ScreenField findField(Long screenFieldId) {
        return screenFieldRepository.findByIdWithScreen(screenFieldId)
                .orElseThrow(() -> new BusinessException(
                        "ScreenField bulunamadı: " + screenFieldId,
                        HttpStatus.NOT_FOUND,
                        "UI_SCREEN_FIELD_NOT_FOUND"));
    }

    private FieldBehaviorRule findRule(Long ruleId) {
        return ruleRepository.findByIdWithScreenField(ruleId)
                .orElseThrow(() -> new BusinessException(
                        "FieldBehaviorRule bulunamadı: " + ruleId,
                        HttpStatus.NOT_FOUND,
                        "UI_RULE_NOT_FOUND"));
    }

    // ── Küçük Yardımcılar ────────────────────────────────────────────────────

    private ConfigChangeEvent.FieldChange change(String field, String oldVal, String newVal) {
        return new ConfigChangeEvent.FieldChange(field, oldVal, newVal);
    }

    private boolean eqNullable(String a, String b) {
        return java.util.Objects.equals(a, b);
    }

    private String uuidStr(Long uuid) {
        return uuid != null ? uuid.toString() : null;
    }
}

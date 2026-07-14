package com.wms.core.service;

import com.wms.core.dto.ui.*;
import com.wms.core.entity.ColumnBehaviorRule;
import com.wms.core.entity.Screen;
import com.wms.core.entity.TableColumnDef;
import com.wms.core.event.ConfigChangeEvent;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.ColumnBehaviorRuleRepository;
import com.wms.core.repository.ScreenRepository;
import com.wms.core.repository.TableColumnDefRepository;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.util.DimensionCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TableColumnAdminService {

    private static final int PRIORITY_WAREHOUSE     = 60;
    private static final int PRIORITY_ROLE          = 40;
    private static final int PRIORITY_CUSTOMER_TYPE = 36;
    private static final int PRIORITY_PRODUCT_TYPE  = 34;
    private static final int PRIORITY_TXN_STATUS    = 32;
    private static final int PRIORITY_COMPANY       = 30;
    private static final int PRIORITY_GLOBAL        = 10;

    private final ScreenRepository screenRepository;
    private final TableColumnDefRepository columnDefRepository;
    private final ColumnBehaviorRuleRepository columnRuleRepository;
    private final DynamicTableUiService tableUiService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<ColumnDefResponse> listColumnDefs(String screenCode) {
        Screen screen = findScreen(screenCode);
        return columnDefRepository.findByScreenIdOrderByDefaultSequenceAsc(screen.getId()).stream()
                .map(d -> toDefResponse(d, screenCode))
                .toList();
    }

    public ColumnDefResponse createColumnDef(String screenCode, UpsertColumnDefRequest request) {
        Screen screen = findScreen(screenCode);
        if (columnDefRepository.existsByScreenIdAndColumnKey(screen.getId(), request.columnKey())) {
            throw conflict("Kolon anahtarı zaten mevcut: " + request.columnKey());
        }

        TableColumnDef def = TableColumnDef.builder()
                .screen(screen)
                .columnKey(request.columnKey().trim())
                .labelKey(request.labelKey().trim())
                .dataType(request.dataType())
                .defaultVisible(request.defaultVisible())
                .defaultSequence(request.defaultSequence())
                .locked(request.locked())
                .renderHint(trimToNull(request.renderHint()))
                .build();
        columnDefRepository.save(def);

        audit("TableColumnDef", def.getId(), "CREATE", List.of(
                new ConfigChangeEvent.FieldChange("columnKey", null, def.getColumnKey())));
        tableUiService.evictScreenTableCache(screenCode);

        return toDefResponse(def, screenCode);
    }

    public ColumnDefResponse updateColumnDef(String screenCode, Long columnDefId, UpsertColumnDefRequest request) {
        TableColumnDef def = findColumnDef(screenCode, columnDefId);
        def.setLabelKey(request.labelKey().trim());
        def.setDataType(request.dataType());
        def.setDefaultVisible(request.defaultVisible());
        def.setDefaultSequence(request.defaultSequence());
        def.setLocked(request.locked());
        def.setRenderHint(trimToNull(request.renderHint()));
        columnDefRepository.save(def);

        audit("TableColumnDef", columnDefId, "UPDATE", List.of(
                new ConfigChangeEvent.FieldChange("labelKey", null, def.getLabelKey())));
        tableUiService.evictScreenTableCache(screenCode);

        return toDefResponse(def, screenCode);
    }

    public void deleteColumnDef(String screenCode, Long columnDefId) {
        TableColumnDef def = findColumnDef(screenCode, columnDefId);
        if (def.isLocked()) {
            throw conflict("Kilitli kolon silinemez: " + def.getColumnKey());
        }
        columnDefRepository.delete(def);
        audit("TableColumnDef", columnDefId, "DELETE", List.of(
                new ConfigChangeEvent.FieldChange("columnKey", def.getColumnKey(), null)));
        tableUiService.evictScreenTableCache(screenCode);
    }

    public ColumnRuleResponse createColumnRule(UpsertColumnRuleRequest request) {
        TableColumnDef def = columnDefRepository.findById(request.tableColumnDefId())
                .orElseThrow(() -> notFound("Kolon tanımı bulunamadı: " + request.tableColumnDefId()));

        int priority = request.priority() != null ? request.priority() : computePriority(request);

        ColumnBehaviorRule rule = ColumnBehaviorRule.builder()
                .tableColumnDef(def)
                .priority(priority)
                .roleId(request.roleId())
                .companyId(request.companyId())
                .warehouseId(request.warehouseId())
                .customerType(DimensionCode.normalizeAndValidate(request.customerType(), "customerType"))
                .productType(DimensionCode.normalizeAndValidate(request.productType(), "productType"))
                .transactionStatus(DimensionCode.normalizeAndValidate(request.transactionStatus(), "transactionStatus"))
                .behavior(request.behavior())
                .build();
        columnRuleRepository.save(rule);

        String screenCode = def.getScreen().getCode();
        audit("ColumnBehaviorRule", rule.getId(), "CREATE", buildColumnRuleCreateChanges(rule));
        tableUiService.evictScreenTableCache(screenCode);

        return toRuleResponse(rule);
    }

    private List<ConfigChangeEvent.FieldChange> buildColumnRuleCreateChanges(ColumnBehaviorRule rule) {
        List<ConfigChangeEvent.FieldChange> changes = new ArrayList<>();
        changes.add(new ConfigChangeEvent.FieldChange("behavior", null, rule.getBehavior().name()));
        changes.add(new ConfigChangeEvent.FieldChange("priority", null, String.valueOf(rule.getPriority())));
        if (rule.getRoleId() != null) changes.add(new ConfigChangeEvent.FieldChange("roleId", null, rule.getRoleId().toString()));
        if (rule.getCompanyId() != null) changes.add(new ConfigChangeEvent.FieldChange("companyId", null, rule.getCompanyId().toString()));
        if (rule.getWarehouseId() != null) changes.add(new ConfigChangeEvent.FieldChange("warehouseId", null, rule.getWarehouseId().toString()));
        if (rule.getCustomerType() != null) changes.add(new ConfigChangeEvent.FieldChange("customerType", null, rule.getCustomerType()));
        if (rule.getProductType() != null) changes.add(new ConfigChangeEvent.FieldChange("productType", null, rule.getProductType()));
        if (rule.getTransactionStatus() != null) changes.add(new ConfigChangeEvent.FieldChange("transactionStatus", null, rule.getTransactionStatus()));
        return changes;
    }

    public void deleteColumnRule(Long ruleId) {
        ColumnBehaviorRule rule = columnRuleRepository.findById(ruleId)
                .orElseThrow(() -> notFound("Kural bulunamadı: " + ruleId));
        String screenCode = rule.getTableColumnDef().getScreen().getCode();
        columnRuleRepository.delete(rule);
        audit("ColumnBehaviorRule", ruleId, "DELETE", List.of());
        tableUiService.evictScreenTableCache(screenCode);
    }

    @Transactional(readOnly = true)
    public List<ColumnRuleResponse> listColumnRules(String screenCode) {
        return columnRuleRepository.findAllByScreenCode(screenCode).stream()
                .map(this::toRuleResponse)
                .toList();
    }

    private int computePriority(UpsertColumnRuleRequest request) {
        if (request.warehouseId()       != null) return PRIORITY_WAREHOUSE;
        if (request.roleId()            != null) return PRIORITY_ROLE;
        if (request.customerType()      != null) return PRIORITY_CUSTOMER_TYPE;
        if (request.productType()       != null) return PRIORITY_PRODUCT_TYPE;
        if (request.transactionStatus() != null) return PRIORITY_TXN_STATUS;
        if (request.companyId()         != null) return PRIORITY_COMPANY;
        return PRIORITY_GLOBAL;
    }

    private TableColumnDef findColumnDef(String screenCode, Long columnDefId) {
        Screen screen = findScreen(screenCode);
        return columnDefRepository.findById(columnDefId)
                .filter(d -> d.getScreen().getId().equals(screen.getId()))
                .orElseThrow(() -> notFound("Kolon bulunamadı: " + columnDefId));
    }

    private Screen findScreen(String screenCode) {
        return screenRepository.findByCode(screenCode)
                .orElseThrow(() -> notFound("Ekran bulunamadı: " + screenCode));
    }

    private ColumnDefResponse toDefResponse(TableColumnDef d, String screenCode) {
        return new ColumnDefResponse(
                d.getId(), screenCode, d.getColumnKey(), d.getLabelKey(),
                d.getDataType(), d.isDefaultVisible(), d.getDefaultSequence(),
                d.isLocked(), d.getRenderHint());
    }

    private ColumnRuleResponse toRuleResponse(ColumnBehaviorRule r) {
        return new ColumnRuleResponse(
                r.getId(),
                r.getTableColumnDef().getId(),
                r.getTableColumnDef().getColumnKey(),
                r.getPriority(),
                r.getRoleId(),
                r.getCompanyId(),
                r.getWarehouseId(),
                r.getCustomerType(),
                r.getProductType(),
                r.getTransactionStatus(),
                r.getBehavior());
    }

    private void audit(String entity, Long id, String action, List<ConfigChangeEvent.FieldChange> changes) {
        Long userId = TenantContextHolder.getContext().map(c -> c.userId()).orElse(null);
        eventPublisher.publishEvent(new ConfigChangeEvent(this, entity, id, action, changes, userId));
    }

    private String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private BusinessException conflict(String msg) {
        return new BusinessException(msg, HttpStatus.CONFLICT, "UI_TABLE_CONFLICT");
    }

    private BusinessException notFound(String msg) {
        return new BusinessException(msg, HttpStatus.NOT_FOUND, "UI_TABLE_NOT_FOUND");
    }
}

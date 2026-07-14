package com.wms.core.service;

import com.wms.core.dto.ui.ColumnPreferenceEntry;
import com.wms.core.dto.ui.ResolvedColumnDto;
import com.wms.core.dto.ui.ResolvedTableSchemaDto;
import com.wms.core.dto.ui.TablePreferenceRequest;
import com.wms.core.dto.ui.UiContext;
import com.wms.core.entity.ColumnBehaviorRule;
import com.wms.core.entity.Screen;
import com.wms.core.entity.TableColumnDef;
import com.wms.core.entity.UserTablePreference;
import com.wms.core.entity.enums.ColumnBehavior;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.ColumnBehaviorRuleRepository;
import com.wms.core.repository.ScreenRepository;
import com.wms.core.repository.TableColumnDefRepository;
import com.wms.core.repository.UserTablePreferenceRepository;
import com.wms.core.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dinamik tablo kolon çözümleme motoru (İş İsteri 16).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DynamicTableUiService {

    private static final String CACHE_KEY_PREFIX = "ui:table:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final ScreenRepository screenRepository;
    private final TableColumnDefRepository columnDefRepository;
    private final ColumnBehaviorRuleRepository columnRuleRepository;
    private final UserTablePreferenceRepository preferenceRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional(readOnly = true)
    public ResolvedTableSchemaDto getResolvedTableSchema(String screenCode, UiContext context) {
        Long userId = TenantContextHolder.require().userId();
        String cacheKey = buildCacheKey(screenCode, userId, context);

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof ResolvedTableSchemaDto dto) {
            log.debug("[TableUI] Cache hit — key={}", cacheKey);
            return dto;
        }

        ResolvedTableSchemaDto resolved = resolveFromDatabase(screenCode, userId, context);
        redisTemplate.opsForValue().set(cacheKey, resolved, CACHE_TTL);
        return resolved;
    }

    public void savePreferences(String screenCode, TablePreferenceRequest request) {
        Long userId = TenantContextHolder.require().userId();
        Screen screen = findScreen(screenCode);

        validatePreferenceKeys(screen.getId(), request.columns());

        UserTablePreference pref = preferenceRepository
                .findByUserIdAndScreenId(userId, screen.getId())
                .orElse(UserTablePreference.builder()
                        .userId(userId)
                        .screen(screen)
                        .preferences(new ArrayList<>())
                        .build());

        pref.setPreferences(new ArrayList<>(request.columns()));
        preferenceRepository.save(pref);

        evictUserTableCache(screenCode, userId);
        log.info("[TableUI] Preferences saved. screen={} userId={} cols={}",
                screenCode, userId, request.columns().size());
    }

    public void deletePreferences(String screenCode) {
        Long userId = TenantContextHolder.require().userId();
        Screen screen = findScreen(screenCode);
        preferenceRepository.deleteByUserIdAndScreenId(userId, screen.getId());
        evictUserTableCache(screenCode, userId);
        log.info("[TableUI] Preferences reset. screen={} userId={}", screenCode, userId);
    }

    public void evictScreenTableCache(String screenCode) {
        String pattern = CACHE_KEY_PREFIX + screenCode + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("[TableUI] Cache evicted — pattern={}, count={}", pattern, keys.size());
        }
    }

    private ResolvedTableSchemaDto resolveFromDatabase(String screenCode, Long userId, UiContext context) {
        Screen screen = findScreen(screenCode);
        List<TableColumnDef> defs = columnDefRepository.findByScreenIdOrderByDefaultSequenceAsc(screen.getId());
        if (defs.isEmpty()) {
            throw new BusinessException(
                    "Tablo kolon tanımı bulunamadı: " + screenCode,
                    HttpStatus.NOT_FOUND,
                    "UI_TABLE_SCHEMA_NOT_FOUND");
        }

        List<ColumnBehaviorRule> rules = columnRuleRepository.findAllByScreenCode(screenCode);
        Map<Long, List<ColumnBehaviorRule>> rulesByColumn = rules.stream()
                .collect(Collectors.groupingBy(r -> r.getTableColumnDef().getId()));

        Map<String, ColumnPreferenceEntry> userPrefs = preferenceRepository
                .findByUserIdAndScreenId(userId, screen.getId())
                .map(p -> p.getPreferences().stream()
                        .collect(Collectors.toMap(ColumnPreferenceEntry::key, e -> e, (a, b) -> b)))
                .orElse(Map.of());

        Set<String> validKeys = defs.stream().map(TableColumnDef::getColumnKey).collect(Collectors.toSet());

        List<ResolvedColumnDto> columns = new ArrayList<>();
        for (TableColumnDef def : defs) {
            ResolvedColumnDto col = resolveColumn(def, rulesByColumn.getOrDefault(def.getId(), List.of()), context, userPrefs);
            columns.add(col);
        }

        columns.sort(Comparator.comparingInt(ResolvedColumnDto::sequence));

        return new ResolvedTableSchemaDto(screenCode, columns, OffsetDateTime.now());
    }

    private ResolvedColumnDto resolveColumn(
            TableColumnDef def,
            List<ColumnBehaviorRule> columnRules,
            UiContext context,
            Map<String, ColumnPreferenceEntry> userPrefs) {

        boolean locked = def.isLocked();
        boolean visible = def.isDefaultVisible();
        int sequence = def.getDefaultSequence();
        boolean forceHidden = false;
        boolean forceVisible = false;

        // Kazanan: önce spesifiklik (dolu boyut sayısı), eşitlikte priority
        // (İş İsteri 2.1, Madde 8.3 — DynamicUiService ile aynı semantik)
        ColumnBehaviorRule winning = columnRules.stream()
                .filter(r -> matchesColumnRule(r, context))
                .max(Comparator.comparingInt(DynamicTableUiService::specificityScore)
                        .thenComparingInt(ColumnBehaviorRule::getPriority))
                .orElse(null);

        if (winning != null) {
            if (winning.getBehavior() == ColumnBehavior.FORCE_HIDDEN) {
                forceHidden = true;
                visible = false;
            } else if (winning.getBehavior() == ColumnBehavior.FORCE_VISIBLE) {
                forceVisible = true;
                visible = true;
                locked = true;
            }
        }

        ColumnPreferenceEntry pref = userPrefs.get(def.getColumnKey());
        if (pref != null && !forceHidden && !forceVisible) {
            visible = pref.visible();
            sequence = pref.sequence();
        }

        if (locked && !forceHidden) {
            visible = true;
        }

        return new ResolvedColumnDto(
                def.getId(),
                def.getColumnKey(),
                def.getLabelKey(),
                def.getDataType(),
                visible,
                sequence,
                locked,
                def.getRenderHint(),
                forceHidden,
                forceVisible
        );
    }

    private boolean matchesColumnRule(ColumnBehaviorRule rule, UiContext ctx) {
        return nullOrEquals(rule.getRoleId(), ctx.roleId())
                && nullOrEquals(rule.getCompanyId(), ctx.companyId())
                && nullOrEquals(rule.getWarehouseId(), ctx.warehouseId())
                && nullOrEqualsStr(rule.getCustomerType(), ctx.customerType())
                && nullOrEqualsStr(rule.getProductType(), ctx.productType())
                && nullOrEqualsStr(rule.getTransactionStatus(), ctx.transactionStatus());
    }

    /** Spesifiklik skoru = dolu (non-null) bağlam boyutu sayısı. */
    static int specificityScore(ColumnBehaviorRule rule) {
        int score = 0;
        if (rule.getRoleId()            != null) score++;
        if (rule.getCompanyId()         != null) score++;
        if (rule.getWarehouseId()       != null) score++;
        if (rule.getCustomerType()      != null) score++;
        if (rule.getProductType()       != null) score++;
        if (rule.getTransactionStatus() != null) score++;
        return score;
    }

    private boolean nullOrEquals(Long ruleValue, Long ctxValue) {
        return ruleValue == null || ruleValue.equals(ctxValue);
    }

    private boolean nullOrEqualsStr(String ruleValue, String ctxValue) {
        return ruleValue == null || ruleValue.equals(ctxValue);
    }

    private void validatePreferenceKeys(Long screenId, List<ColumnPreferenceEntry> columns) {
        Set<String> validKeys = columnDefRepository.findByScreenIdOrderByDefaultSequenceAsc(screenId).stream()
                .map(TableColumnDef::getColumnKey)
                .collect(Collectors.toSet());
        for (ColumnPreferenceEntry entry : columns) {
            if (!validKeys.contains(entry.key())) {
                throw new BusinessException(
                        "Bilinmeyen kolon anahtarı: " + entry.key(),
                        HttpStatus.BAD_REQUEST,
                        "UI_TABLE_UNKNOWN_COLUMN");
            }
        }
    }

    private Screen findScreen(String screenCode) {
        return screenRepository.findByCode(screenCode)
                .orElseThrow(() -> new BusinessException(
                        "Ekran bulunamadı: " + screenCode,
                        HttpStatus.NOT_FOUND,
                        "UI_SCREEN_NOT_FOUND"));
    }

    private void evictUserTableCache(String screenCode, Long userId) {
        String pattern = CACHE_KEY_PREFIX + screenCode + ":" + userId + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String buildCacheKey(String screenCode, Long userId, UiContext ctx) {
        return CACHE_KEY_PREFIX + screenCode + ":" + userId + ":" + ctx.cacheKeySuffix();
    }
}

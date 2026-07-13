package com.wms.core.service;

import com.wms.core.dto.ui.ResolvedFieldDto;
import com.wms.core.dto.ui.ResolvedScreenDto;
import com.wms.core.dto.ui.UiContext;
import com.wms.core.entity.FieldBehaviorRule;
import com.wms.core.entity.Screen;
import com.wms.core.entity.ScreenField;
import com.wms.core.entity.enums.FieldBehavior;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.FieldBehaviorRuleRepository;
import com.wms.core.repository.ScreenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dinamik UI kural çözümleme motoru.
 *
 * <h3>Sorumluluklar</h3>
 * <ol>
 *   <li>Verilen {@code screenCode} + {@link UiContext} için Redis cache'ini sorgular.</li>
 *   <li>Cache miss durumunda DB'den ekranı ve tüm kurallarını çeker.</li>
 *   <li>Her alan için bağlam eşleşen kuralları bulur, en yüksek priority'yi seçer.</li>
 *   <li>Çözümlenmiş {@link ResolvedScreenDto}'yu Redis'e yazar ve döner.</li>
 * </ol>
 *
 * <h3>Cache Key Deseni</h3>
 * <pre>ui:{screenCode}:{locationId}:{roleId}:{companyId}:{countryId}:{operationType}</pre>
 *
 * <h3>Kural Eşleşme Algoritması</h3>
 * <p>Bir kural, aşağıdaki koşulların tamamını sağladığında eşleşir:</p>
 * <ul>
 *   <li>{@code rule.locationId    == null || rule.locationId    == ctx.locationId}</li>
 *   <li>{@code rule.roleId        == null || rule.roleId        == ctx.roleId}</li>
 *   <li>{@code rule.companyId     == null || rule.companyId     == ctx.companyId}</li>
 *   <li>{@code rule.countryId     == null || rule.countryId     == ctx.countryId}</li>
 *   <li>{@code rule.operationType == null || rule.operationType.equals(ctx.operationType)}</li>
 * </ul>
 * <p>Eşleşen kurallar arasında en yüksek {@code priority} değerine sahip olan seçilir.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DynamicUiService {

    private static final String CACHE_KEY_PREFIX = "ui:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final ScreenRepository screenRepository;
    private final FieldBehaviorRuleRepository ruleRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Belirtilen ekranın, verilen bağlamda çözümlenmiş form şemasını döner.
     *
     * <p>Önce Redis cache kontrol edilir. Cache miss durumunda DB'den hesaplanır
     * ve cache'e yazılır.</p>
     *
     * @param screenCode ekran kodu, örn: {@code REC_CONTROL_FORM}
     * @param context    çalışma zamanı bağlamı
     * @return çözümlenmiş form şeması
     * @throws BusinessException ekran bulunamazsa → HTTP 404
     */
    public ResolvedScreenDto getResolvedScreen(String screenCode, UiContext context) {
        String cacheKey = buildCacheKey(screenCode, context);

        // 1. Cache hit
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof ResolvedScreenDto dto) {
            log.debug("[DynamicUI] Cache hit — key={}", cacheKey);
            return dto;
        }

        // 2. Cache miss → DB'den çöz
        log.debug("[DynamicUI] Cache miss — key={}, resolving from DB", cacheKey);
        ResolvedScreenDto resolved = resolveFromDatabase(screenCode, context);

        // 3. Cache'e yaz
        redisTemplate.opsForValue().set(cacheKey, resolved, CACHE_TTL);

        return resolved;
    }

    /**
     * Belirtilen ekrana ait tüm cache girdilerini siler.
     * Kural güncelleme/silme işlemlerinden sonra {@link UiRuleManagementService} tarafından çağrılır.
     *
     * @param screenCode ekranın kodu
     */
    public void evictScreenCache(String screenCode) {
        String pattern = CACHE_KEY_PREFIX + screenCode + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("[DynamicUI] Cache evicted — pattern={}, count={}", pattern, keys.size());
        }
    }

    // ── Kural Çözümleme ───────────────────────────────────────────────────────

    /**
     * DB'den ekranı ve tüm kurallarını çekerek form şemasını çözer.
     */
    private ResolvedScreenDto resolveFromDatabase(String screenCode, UiContext context) {
        Screen screen = screenRepository.findByCodeWithFields(screenCode)
                .orElseThrow(() -> new BusinessException(
                        "Ekran bulunamadı: " + screenCode,
                        HttpStatus.NOT_FOUND,
                        "UI_SCREEN_NOT_FOUND"));

        // Tüm kuralları tek sorguda çek (N+1 yok)
        List<FieldBehaviorRule> allRules = ruleRepository.findAllByScreenCode(screenCode);

        // Alan → kural listesi eşlemesi (in-memory grouping)
        Map<Long, List<FieldBehaviorRule>> rulesByField = allRules.stream()
                .collect(Collectors.groupingBy(r -> r.getScreenField().getId()));

        List<ResolvedFieldDto> resolvedFields = screen.getFields().stream()
                .map(field -> resolveField(field, rulesByField.getOrDefault(field.getId(), List.of()), context))
                .toList();

        return new ResolvedScreenDto(
                screen.getCode(),
                screen.getName(),
                ResolvedScreenDto.toScreenNameKey(screen.getCode()),
                resolvedFields,
                OffsetDateTime.now()
        );
    }

    /**
     * Tek bir alan için kural çözümlemesi yapar.
     *
     * <ol>
     *   <li>Bağlam eşleşen kuralları filtrele.</li>
     *   <li>Priority büyükten küçüğe sırala.</li>
     *   <li>En yüksek priority'li kuralı al → davranış, defaultValue, regex.</li>
     *   <li>Hiç eşleşen kural yoksa → {@code defaultBehavior} uygula.</li>
     * </ol>
     */
    private ResolvedFieldDto resolveField(ScreenField field,
                                          List<FieldBehaviorRule> fieldRules,
                                          UiContext context) {
        FieldBehaviorRule winningRule = fieldRules.stream()
                .filter(rule -> matches(rule, context))
                .max(Comparator.comparingInt(FieldBehaviorRule::getPriority))
                .orElse(null);

        FieldBehavior behavior      = winningRule != null ? winningRule.getBehavior()         : field.getDefaultBehavior();
        String        defaultValue  = winningRule != null ? winningRule.getDefaultValue()     : null;
        String        regex         = winningRule != null ? winningRule.getValidationRegex()  : null;
        String        regexMsgKey   = winningRule != null ? winningRule.getValidationErrorMessageKey() : null;

        log.trace("[DynamicUI] field={} behavior={} rule={}",
                field.getFieldKey(), behavior,
                winningRule != null ? winningRule.getId() : "default");

        return new ResolvedFieldDto(
                field.getFieldKey(),
                ResolvedFieldDto.toLabelKey(field.getFieldKey()),
                field.getDataType(),
                behavior,
                defaultValue,
                regex,
                regexMsgKey
        );
    }

    /**
     * Bir kuralın verilen bağlamla eşleşip eşleşmediğini kontrol eder.
     *
     * <p>Kural boyutu null ise → "herkes için geçerli" → her zaman eşleşir.<br>
     * Kural boyutu dolu ise → context'teki değerle eşit olmalı.</p>
     */
    private boolean matches(FieldBehaviorRule rule, UiContext ctx) {
        return nullOrEquals(rule.getLocationId(),  ctx.locationId())
            && nullOrEquals(rule.getRoleId(),       ctx.roleId())
            && nullOrEquals(rule.getCompanyId(),    ctx.companyId())
            && nullOrEquals(rule.getCountryId(),    ctx.countryId())
            && nullOrEqualsStr(rule.getOperationType(), ctx.operationType());
    }

    private boolean nullOrEquals(Long ruleValue, Long ctxValue) {
        return ruleValue == null || ruleValue.equals(ctxValue);
    }

    private boolean nullOrEqualsStr(String ruleValue, String ctxValue) {
        return ruleValue == null || ruleValue.equals(ctxValue);
    }

    // ── Cache Key ─────────────────────────────────────────────────────────────

    /**
     * Cache key oluşturur.
     *
     * <p>Pattern: {@code ui:{screenCode}:{locationId}:{roleId}}</p>
     * <p>Null değerler {@code "x"} ile temsil edilir (key uzunluğunu sabit tutar).</p>
     */
    private String buildCacheKey(String screenCode, UiContext ctx) {
        return CACHE_KEY_PREFIX + screenCode + ":" + ctx.cacheKeySuffix();
    }
}

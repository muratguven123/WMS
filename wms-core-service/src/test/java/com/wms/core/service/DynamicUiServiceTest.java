package com.wms.core.service;

import com.wms.core.dto.ui.ResolvedFieldDto;
import com.wms.core.dto.ui.ResolvedScreenDto;
import com.wms.core.dto.ui.UiContext;
import com.wms.core.entity.FieldBehaviorRule;
import com.wms.core.entity.Screen;
import com.wms.core.entity.ScreenField;
import com.wms.core.entity.enums.FieldBehavior;
import com.wms.core.entity.enums.FieldDataType;
import com.wms.core.repository.FieldBehaviorRuleRepository;
import com.wms.core.repository.ScreenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamicUiService")
class DynamicUiServiceTest {

    private static final Long FIELD_TAX_ID = 1001L;
    private static final Long FIELD_DISTRICT_ID = 1002L;
    private static final Long COUNTRY_TR = 1L;
    private static final Long LOCATION_DEMO = 101L;
    private static final Long ROLE_MANAGER = 201L;
    private static final Long LOCATION_ISTANBUL = 301L;

    @Mock private ScreenRepository screenRepository;
    @Mock private FieldBehaviorRuleRepository ruleRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private DynamicUiService dynamicUiService;

    @Test
    @DisplayName("Ülke kuralı eşleştiğinde MANDATORY ve regex uygulanır")
    void resolve_countryRule_appliesMandatoryAndRegex() {
        stubCacheMiss();
        stubScreenWithFields();
        when(ruleRepository.findAllByScreenCode("REC_CONTROL_FORM")).thenReturn(List.of(
                countryTaxRule(),
                globalDistrictRule()
        ));

        UiContext ctx = new UiContext(LOCATION_DEMO, null, null, COUNTRY_TR, "CREATE");
        ResolvedScreenDto result = dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", ctx);

        ResolvedFieldDto taxField = findField(result, "tax_number");
        assertThat(taxField.behavior()).isEqualTo(FieldBehavior.MANDATORY);
        assertThat(taxField.validationRegex()).isEqualTo("^\\d{10}$");

        ResolvedFieldDto districtField = findField(result, "district");
        assertThat(districtField.behavior()).isEqualTo(FieldBehavior.HIDDEN);
    }

    @Test
    @DisplayName("Ülke bağlamı eşleşmezse varsayılan davranış kullanılır")
    void resolve_countryMismatch_fallsBackToDefault() {
        stubCacheMiss();
        stubScreenWithFields();
        when(ruleRepository.findAllByScreenCode("REC_CONTROL_FORM")).thenReturn(List.of(countryTaxRule()));

        UiContext ctx = new UiContext(LOCATION_DEMO, null, null, 999L, "CREATE");
        ResolvedScreenDto result = dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", ctx);

        assertThat(findField(result, "tax_number").behavior()).isEqualTo(FieldBehavior.OPTIONAL);
    }

    @Test
    @DisplayName("Cache key tüm bağlam boyutlarını içerir")
    void cacheKey_includesAllContextDimensions() {
        stubCacheMiss();
        stubScreenWithFields();
        when(ruleRepository.findAllByScreenCode("REC_CONTROL_FORM")).thenReturn(List.of());

        UiContext ctx = new UiContext(LOCATION_DEMO, null, null, COUNTRY_TR, "CREATE");
        dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", ctx);

        String expectedKey = "ui:REC_CONTROL_FORM:" + ctx.cacheKeySuffix();
        verify(valueOperations).set(anyString(), any(ResolvedScreenDto.class), any());
        verify(valueOperations).get(expectedKey);
    }

    // =====================================================================
    // Varsayılan davranış — hiç kural tanımlı değilken
    // =====================================================================

    @Test
    @DisplayName("Hiç kural tanımlı değilken alanlar defaultBehavior ile döner")
    void resolve_noRulesDefined_returnsDefaultBehavior() {
        stubCacheMiss();
        stubScreenWithFields();
        when(ruleRepository.findAllByScreenCode("REC_CONTROL_FORM")).thenReturn(List.of());

        UiContext ctx = new UiContext(LOCATION_ISTANBUL, ROLE_MANAGER, null, null, "CREATE");
        ResolvedScreenDto result = dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", ctx);

        // Modelde "görünür" davranışın karşılığı OPTIONAL'dır (VISIBLE değeri yok)
        assertThat(findField(result, "tax_number").behavior()).isEqualTo(FieldBehavior.OPTIONAL);
        assertThat(findField(result, "district").behavior()).isEqualTo(FieldBehavior.OPTIONAL);
    }

    // =====================================================================
    // Tek kural — Role=Manager
    // =====================================================================

    @Test
    @DisplayName("Tek rol kuralı: Role=Manager bağlamında tetiklenir, farklı rolde default döner")
    void resolve_singleRoleRule_triggersOnlyOnRoleMatch() {
        stubCacheMiss();
        stubScreenWithFields();
        when(ruleRepository.findAllByScreenCode("REC_CONTROL_FORM")).thenReturn(List.of(
                taxRule(10, FieldBehavior.MANDATORY, ROLE_MANAGER, null)
        ));

        // Rol eşleşiyor → kural tetiklenir
        UiContext managerCtx = new UiContext(null, ROLE_MANAGER, null, null, "CREATE");
        ResolvedScreenDto managerResult = dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", managerCtx);
        assertThat(findField(managerResult, "tax_number").behavior()).isEqualTo(FieldBehavior.MANDATORY);

        // Farklı rol → kural eşleşmez, defaultBehavior döner
        UiContext otherCtx = new UiContext(null, 99L, null, null, "CREATE");
        ResolvedScreenDto otherResult = dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", otherCtx);
        assertThat(findField(otherResult, "tax_number").behavior()).isEqualTo(FieldBehavior.OPTIONAL);
    }

    // =====================================================================
    // Priority ezme — aynı alanda birden fazla eşleşen kural
    // =====================================================================

    @Test
    @DisplayName("Çoklu eşleşmede yüksek priority kazanır: Location(20, MANDATORY) > Role(10, OPTIONAL)")
    void resolve_multipleMatchingRules_highestPriorityWins() {
        stubCacheMiss();
        stubScreenWithFields();
        when(ruleRepository.findAllByScreenCode("REC_CONTROL_FORM")).thenReturn(List.of(
                taxRule(10, FieldBehavior.OPTIONAL,  ROLE_MANAGER, null),              // Kural 1: Role=Manager
                taxRule(20, FieldBehavior.MANDATORY, null, LOCATION_ISTANBUL)          // Kural 2: Location=İstanbul
        ));

        // Hem rol hem lokasyon eşleşiyor → priority 20 (MANDATORY) kazanır
        UiContext bothMatch = new UiContext(LOCATION_ISTANBUL, ROLE_MANAGER, null, null, "CREATE");
        ResolvedScreenDto result = dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", bothMatch);
        assertThat(findField(result, "tax_number").behavior()).isEqualTo(FieldBehavior.MANDATORY);

        // Lokasyon farklı → sadece rol kuralı eşleşir; priority yalnızca
        // EŞLEŞEN kurallar arasında işler → OPTIONAL kazanır
        UiContext onlyRole = new UiContext(1L, ROLE_MANAGER, null, null, "CREATE");
        ResolvedScreenDto result2 = dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", onlyRole);
        assertThat(findField(result2, "tax_number").behavior()).isEqualTo(FieldBehavior.OPTIONAL);
    }

    // =====================================================================
    // Redis cache — ikinci çağrıda DB'ye gidilmez
    // =====================================================================

    @Test
    @DisplayName("İkinci çağrı cache'ten döner — DB repository'leri tekrar çağrılmaz")
    void getResolvedScreen_secondCall_servedFromCacheWithoutDbHit() {
        // Gerçekçi cache simülasyonu: set edilen değer sonraki get'te döner
        Map<String, Object> fakeRedis = new HashMap<>();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString()))
                .thenAnswer(inv -> fakeRedis.get(inv.<String>getArgument(0)));
        doAnswer(inv -> {
            fakeRedis.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any(), any(Duration.class));

        stubScreenWithFields();
        when(ruleRepository.findAllByScreenCode("REC_CONTROL_FORM")).thenReturn(List.of());

        UiContext ctx = new UiContext(LOCATION_ISTANBUL, ROLE_MANAGER, null, null, "CREATE");

        ResolvedScreenDto first  = dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", ctx);
        ResolvedScreenDto second = dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", ctx);

        assertThat(second).isSameAs(first);                        // cache'teki instance döndü
        verify(screenRepository, times(1)).findByCodeWithFields("REC_CONTROL_FORM");
        verify(ruleRepository,   times(1)).findAllByScreenCode("REC_CONTROL_FORM");
        verify(valueOperations,  times(1)).set(anyString(), any(), any(Duration.class));
        verify(valueOperations,  times(2)).get(anyString());
    }

    private void stubCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
    }

    private void stubScreenWithFields() {
        Screen screen = Screen.builder()
                .code("REC_CONTROL_FORM")
                .name("Mal Kabul Kontrol Formu")
                .build();

        ScreenField taxField = ScreenField.builder()
                .screen(screen)
                .fieldKey("tax_number")
                .defaultBehavior(FieldBehavior.OPTIONAL)
                .dataType(FieldDataType.STRING)
                .build();
        taxField.setId(FIELD_TAX_ID);

        ScreenField districtField = ScreenField.builder()
                .screen(screen)
                .fieldKey("district")
                .defaultBehavior(FieldBehavior.OPTIONAL)
                .dataType(FieldDataType.STRING)
                .build();
        districtField.setId(FIELD_DISTRICT_ID);

        screen.setFields(List.of(taxField, districtField));
        when(screenRepository.findByCodeWithFields("REC_CONTROL_FORM")).thenReturn(Optional.of(screen));
    }

    private FieldBehaviorRule countryTaxRule() {
        ScreenField taxField = ScreenField.builder().fieldKey("tax_number").build();
        taxField.setId(FIELD_TAX_ID);
        return FieldBehaviorRule.builder()
                .id(1L)
                .screenField(taxField)
                .priority(20)
                .countryId(COUNTRY_TR)
                .behavior(FieldBehavior.MANDATORY)
                .validationRegex("^\\d{10}$")
                .validationErrorMessageKey("validation.tax_number.invalid")
                .build();
    }

    /**
     * tax_number alanına bağlı, rol ve/veya lokasyon boyutlu kural üretir.
     * Null boyut → "herkes için geçerli".
     */
    private FieldBehaviorRule taxRule(int priority, FieldBehavior behavior, Long roleId, Long locationId) {
        ScreenField taxField = ScreenField.builder().fieldKey("tax_number").build();
        taxField.setId(FIELD_TAX_ID);
        return FieldBehaviorRule.builder()
                .id(1L)
                .screenField(taxField)
                .priority(priority)
                .roleId(roleId)
                .locationId(locationId)
                .behavior(behavior)
                .build();
    }

    private FieldBehaviorRule globalDistrictRule() {
        ScreenField districtField = ScreenField.builder().fieldKey("district").build();
        districtField.setId(FIELD_DISTRICT_ID);
        return FieldBehaviorRule.builder()
                .id(1L)
                .screenField(districtField)
                .priority(50)
                .locationId(LOCATION_DEMO)
                .behavior(FieldBehavior.HIDDEN)
                .build();
    }

    private ResolvedFieldDto findField(ResolvedScreenDto screen, String fieldKey) {
        return screen.fields().stream()
                .filter(f -> f.fieldKey().equals(fieldKey))
                .findFirst()
                .orElseThrow();
    }
}

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

    // =====================================================================
    // Yeni Boyutlar ve Öncelik Testleri (İş İsteri 2.1, Madde 8.3)
    // =====================================================================

    @Test
    @DisplayName("(a) Çoklu eşleşmede dolu boyut sayısı (spesifiklik) yüksek olan kural kazanır: Location+Warehouse > Location")
    void resolve_specificityLocationAndWarehouseWinsOverLocationOnly() {
        stubCacheMiss();
        stubScreenWithFields();

        // Location-only kuralı (Priority 50, MANDATORY, spesifiklik = 1)
        // Location+Warehouse kuralı (Priority 10, HIDDEN, spesifiklik = 2)
        // Spesifiklik önceliklidir; location+warehouse (HIDDEN) kazanmalı.
        ScreenField taxField = ScreenField.builder().fieldKey("tax_number").build();
        taxField.setId(FIELD_TAX_ID);

        FieldBehaviorRule locationRule = FieldBehaviorRule.builder()
                .id(1L)
                .screenField(taxField)
                .priority(50)
                .locationId(LOCATION_ISTANBUL)
                .behavior(FieldBehavior.MANDATORY)
                .build();

        FieldBehaviorRule locationWarehouseRule = FieldBehaviorRule.builder()
                .id(2L)
                .screenField(taxField)
                .priority(10)
                .locationId(LOCATION_ISTANBUL)
                .warehouseId(5L)
                .behavior(FieldBehavior.HIDDEN)
                .build();

        when(ruleRepository.findAllByScreenCode("REC_CONTROL_FORM"))
                .thenReturn(List.of(locationRule, locationWarehouseRule));

        // Bağlamda hem lokasyon hem depo var
        UiContext ctx = new UiContext(LOCATION_ISTANBUL, null, null, null, "CREATE", 5L, null, null, null);
        ResolvedScreenDto result = dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", ctx);

        assertThat(findField(result, "tax_number").behavior()).isEqualTo(FieldBehavior.HIDDEN);
    }

    @Test
    @DisplayName("(b) customerType null vs dolu: dolu olan kural eşleşiyorsa kazanır, farklı ise null/global kural kazanır")
    void resolve_customerTypeSpecificVsNull() {
        stubCacheMiss();
        stubScreenWithFields();

        // Global/null customerType kuralı (MANDATORY, spesifiklik = 0)
        // RETAIL customerType kuralı (HIDDEN, spesifiklik = 1)
        ScreenField taxField = ScreenField.builder().fieldKey("tax_number").build();
        taxField.setId(FIELD_TAX_ID);

        FieldBehaviorRule globalRule = FieldBehaviorRule.builder()
                .id(1L)
                .screenField(taxField)
                .priority(10)
                .behavior(FieldBehavior.MANDATORY)
                .build();

        FieldBehaviorRule retailRule = FieldBehaviorRule.builder()
                .id(2L)
                .screenField(taxField)
                .priority(36)
                .customerType("RETAIL")
                .behavior(FieldBehavior.HIDDEN)
                .build();

        when(ruleRepository.findAllByScreenCode("REC_CONTROL_FORM"))
                .thenReturn(List.of(globalRule, retailRule));

        // Durum 1: Context customerType = RETAIL -> Specific rule (HIDDEN) wins
        UiContext retailCtx = new UiContext(null, null, null, null, "CREATE", null, "RETAIL", null, null);
        ResolvedScreenDto retailResult = dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", retailCtx);
        assertThat(findField(retailResult, "tax_number").behavior()).isEqualTo(FieldBehavior.HIDDEN);

        // Durum 2: Context customerType = WHOLESALE -> Specific rule mismatches, global rule (MANDATORY) wins
        UiContext wholesaleCtx = new UiContext(null, null, null, null, "CREATE", null, "WHOLESALE", null, null);
        ResolvedScreenDto wholesaleResult = dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", wholesaleCtx);
        assertThat(findField(wholesaleResult, "tax_number").behavior()).isEqualTo(FieldBehavior.MANDATORY);
    }

    @Test
    @DisplayName("(c) cache key ayrışması: farklı bağlam boyutları farklı cache key'leri üretir")
    void resolve_cacheKeySeparationForNewDimensions() {
        stubCacheMiss();
        stubScreenWithFields();
        when(ruleRepository.findAllByScreenCode("REC_CONTROL_FORM")).thenReturn(List.of());

        // Context 1: warehouse 5, customerType RETAIL
        UiContext ctx1 = new UiContext(LOCATION_ISTANBUL, null, null, null, "CREATE", 5L, "RETAIL", null, null);
        dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", ctx1);
        String expectedKey1 = "ui:REC_CONTROL_FORM:" + ctx1.cacheKeySuffix();
        verify(valueOperations).get(expectedKey1);

        // Context 2: warehouse 5, customerType WHOLESALE
        UiContext ctx2 = new UiContext(LOCATION_ISTANBUL, null, null, null, "CREATE", 5L, "WHOLESALE", null, null);
        dynamicUiService.getResolvedScreen("REC_CONTROL_FORM", ctx2);
        String expectedKey2 = "ui:REC_CONTROL_FORM:" + ctx2.cacheKeySuffix();
        verify(valueOperations).get(expectedKey2);

        // Cache key'lerin birbirinden farklı olduğunu doğrula
        assertThat(expectedKey1).isNotEqualTo(expectedKey2);
        assertThat(expectedKey1).contains(":5:RETAIL:x:x");
        assertThat(expectedKey2).contains(":5:WHOLESALE:x:x");
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

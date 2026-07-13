package com.wms.core.service;

import com.wms.core.dto.ui.ColumnPreferenceEntry;
import com.wms.core.dto.ui.ResolvedColumnDto;
import com.wms.core.dto.ui.ResolvedTableSchemaDto;
import com.wms.core.dto.ui.UiContext;
import com.wms.core.entity.ColumnBehaviorRule;
import com.wms.core.entity.Screen;
import com.wms.core.entity.TableColumnDef;
import com.wms.core.entity.UserTablePreference;
import com.wms.core.entity.enums.ColumnBehavior;
import com.wms.core.entity.enums.ColumnDataType;
import com.wms.core.repository.ColumnBehaviorRuleRepository;
import com.wms.core.repository.ScreenRepository;
import com.wms.core.repository.TableColumnDefRepository;
import com.wms.core.repository.UserTablePreferenceRepository;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamicTableUiService")
class DynamicTableUiServiceTest {

    @Mock private ScreenRepository screenRepository;
    @Mock private TableColumnDefRepository columnDefRepository;
    @Mock private ColumnBehaviorRuleRepository columnRuleRepository;
    @Mock private UserTablePreferenceRepository preferenceRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private DynamicTableUiService service;

    private static final Long USER_ID = 5L;
    private static final Long SCREEN_ID = 100L;
    private static final UiContext CTX = new UiContext(1L, 2L, 1L, null, null);

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setContext(new TenantContext(USER_ID, 1L, 1L));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("FORCE_HIDDEN kolonu gizler ve kullanıcı tercihini ezer")
    void forceHiddenOverridesUserPreference() {
        Screen screen = Screen.builder().code("ADDRESS_LIST").name("Address").build();
        screen.setId(SCREEN_ID);

        TableColumnDef stateCol = column("state", 2, true);
        stateCol.setId(10L);

        ColumnBehaviorRule hiddenRule = ColumnBehaviorRule.builder()
                .tableColumnDef(stateCol)
                .behavior(ColumnBehavior.FORCE_HIDDEN)
                .priority(40)
                .roleId(2L)
                .build();

        when(screenRepository.findByCode("ADDRESS_LIST")).thenReturn(Optional.of(screen));
        when(columnDefRepository.findByScreenIdOrderByDefaultSequenceAsc(SCREEN_ID))
                .thenReturn(List.of(column("id", 0, true), stateCol));
        when(columnRuleRepository.findAllByScreenCode("ADDRESS_LIST")).thenReturn(List.of(hiddenRule));
        when(preferenceRepository.findByUserIdAndScreenId(USER_ID, SCREEN_ID))
                .thenReturn(Optional.of(UserTablePreference.builder()
                        .preferences(List.of(new ColumnPreferenceEntry("state", true, 2, null)))
                        .build()));

        ResolvedTableSchemaDto schema = service.getResolvedTableSchema("ADDRESS_LIST", CTX);

        ResolvedColumnDto state = schema.columns().stream().filter(c -> c.key().equals("state")).findFirst().orElseThrow();
        assertThat(state.visible()).isFalse();
        assertThat(state.forceHidden()).isTrue();
    }

    @Test
    @DisplayName("locked kolon her zaman görünür")
    void lockedColumnAlwaysVisible() {
        Screen screen = Screen.builder().code("ADDRESS_LIST").name("Address").build();
        screen.setId(SCREEN_ID);

        TableColumnDef idCol = column("id", 0, true);
        idCol.setId(1L);

        when(screenRepository.findByCode("ADDRESS_LIST")).thenReturn(Optional.of(screen));
        when(columnDefRepository.findByScreenIdOrderByDefaultSequenceAsc(SCREEN_ID)).thenReturn(List.of(idCol));
        when(columnRuleRepository.findAllByScreenCode("ADDRESS_LIST")).thenReturn(List.of());
        when(preferenceRepository.findByUserIdAndScreenId(USER_ID, SCREEN_ID)).thenReturn(Optional.empty());

        ResolvedTableSchemaDto schema = service.getResolvedTableSchema("ADDRESS_LIST", CTX);
        assertThat(schema.columns().get(0).visible()).isTrue();
        assertThat(schema.columns().get(0).locked()).isTrue();
    }

    private TableColumnDef column(String key, int seq, boolean locked) {
        return TableColumnDef.builder()
                .columnKey(key)
                .labelKey("columns.test." + key)
                .dataType(ColumnDataType.STRING)
                .defaultVisible(true)
                .defaultSequence(seq)
                .locked(locked)
                .build();
    }
}

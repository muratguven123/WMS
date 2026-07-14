package com.wms.core.controller;

import com.wms.core.dto.ui.ResolvedColumnDto;
import com.wms.core.dto.ui.ResolvedFieldDto;
import com.wms.core.dto.ui.ResolvedScreenDto;
import com.wms.core.dto.ui.ResolvedTableSchemaDto;
import com.wms.core.dto.ui.UiContext;
import com.wms.core.entity.enums.ColumnDataType;
import com.wms.core.entity.enums.FieldBehavior;
import com.wms.core.entity.enums.FieldDataType;
import com.wms.core.exception.GlobalExceptionHandler;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextFilter;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.service.DynamicUiService;
import com.wms.core.service.DynamicTableUiService;
import com.wms.core.service.UiContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DynamicUiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("DynamicUiController")
class DynamicUiControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long COMPANY_ID = 1L;
    private static final Long LOCATION_ID = 1L;

    @Autowired private MockMvc mockMvc;

    @MockBean private DynamicUiService dynamicUiService;
    @MockBean private DynamicTableUiService dynamicTableUiService;
    @MockBean private UiContextFactory uiContextFactory;
    @MockBean private TenantContextFilter tenantContextFilter;

    @BeforeEach
    void setContext() {
        TenantContextHolder.setContext(new TenantContext(USER_ID, COMPANY_ID, LOCATION_ID));
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("GET /api/ui/screens/{code}/schema — çözümlenmiş form şemasını döner")
    void getSchema_returnsResolvedScreen() throws Exception {
        Long roleId = 1L;
        Long countryId = 1L;
        UiContext context = new UiContext(LOCATION_ID, roleId, COMPANY_ID, countryId, "CREATE");

        ResolvedScreenDto schema = new ResolvedScreenDto(
                "REC_CONTROL_FORM",
                "Mal Kabul Kontrol Formu",
                "screen.REC_CONTROL_FORM.name",
                List.of(new ResolvedFieldDto(
                        "tax_number",
                        "field.tax_number.label",
                        FieldDataType.STRING,
                        FieldBehavior.MANDATORY,
                        null,
                        null,
                        null)),
                OffsetDateTime.parse("2026-07-05T10:00:00+03:00")
        );

        when(uiContextFactory.fromTenant(
                eq(LOCATION_ID), eq(COMPANY_ID), eq(roleId), eq(countryId), eq("CREATE"),
                eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(context);
        when(dynamicUiService.getResolvedScreen(eq("REC_CONTROL_FORM"), eq(context)))
                .thenReturn(schema);

        mockMvc.perform(get("/api/ui/screens/{screenCode}/schema", "REC_CONTROL_FORM")
                        .param("roleId", roleId.toString())
                        .param("countryId", countryId.toString())
                        .param("operationType", "CREATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenCode").value("REC_CONTROL_FORM"))
                .andExpect(jsonPath("$.screenName").value("Mal Kabul Kontrol Formu"))
                .andExpect(jsonPath("$.fields[0].fieldKey").value("tax_number"))
                .andExpect(jsonPath("$.fields[0].behavior").value("MANDATORY"));
    }

    @Test
    @DisplayName("GET /api/ui/screens/{code}/table-schema — çözümlenmiş tablo şemasını döner")
    void getTableSchema_returnsResolvedColumns() throws Exception {
        Long roleId = 1L;
        UiContext context = new UiContext(LOCATION_ID, roleId, COMPANY_ID, null, null);

        ResolvedTableSchemaDto schema = new ResolvedTableSchemaDto(
                "ADDRESS_LIST",
                List.of(new ResolvedColumnDto(
                        1L, "id", "columns.common.id",
                        ColumnDataType.NUMBER,
                        true, 0, true, null, false, false)),
                OffsetDateTime.parse("2026-07-09T10:00:00+03:00")
        );

        when(uiContextFactory.fromTenant(
                eq(LOCATION_ID), eq(COMPANY_ID), eq(roleId), eq(null), eq(null),
                eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(context);
        when(dynamicTableUiService.getResolvedTableSchema(eq("ADDRESS_LIST"), eq(context)))
                .thenReturn(schema);

        mockMvc.perform(get("/api/ui/screens/{screenCode}/table-schema", "ADDRESS_LIST")
                        .param("roleId", roleId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenCode").value("ADDRESS_LIST"))
                .andExpect(jsonPath("$.columns[0].key").value("id"))
                .andExpect(jsonPath("$.columns[0].locked").value(true));
    }
}

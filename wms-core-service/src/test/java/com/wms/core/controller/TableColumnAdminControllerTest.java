package com.wms.core.controller;

import com.wms.core.dto.ui.ColumnDefResponse;
import com.wms.core.dto.ui.UpsertColumnDefRequest;
import com.wms.core.entity.enums.ColumnDataType;
import com.wms.core.exception.GlobalExceptionHandler;
import com.wms.core.security.TenantContextFilter;
import com.wms.core.service.TableColumnAdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TableColumnAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("TableColumnAdminController")
class TableColumnAdminControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private TableColumnAdminService adminService;
    @MockBean private TenantContextFilter tenantContextFilter;

    @BeforeEach
    void setUpSecurity() {
        ControllerTestSecuritySupport.authenticateAsWmsAdmin();
    }

    @AfterEach
    void clearSecurity() {
        ControllerTestSecuritySupport.clearAuthentication();
    }

    @Test
    @DisplayName("GET /api/ui/screens/{code}/columns — kolon tanımlarını listeler")
    void listColumns_returnsDefs() throws Exception {
        when(adminService.listColumnDefs("ADDRESS_LIST")).thenReturn(List.of(
                new ColumnDefResponse(1L, "ADDRESS_LIST", "id", "columns.common.id",
                        ColumnDataType.NUMBER, true, 0, true, null)));

        mockMvc.perform(get("/api/ui/screens/{screenCode}/columns", "ADDRESS_LIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].columnKey").value("id"));
    }

    @Test
    @DisplayName("POST /api/ui/screens/{code}/columns — yeni kolon oluşturur")
    void createColumn_returnsCreated() throws Exception {
        UpsertColumnDefRequest req = new UpsertColumnDefRequest(
                "notes", "columns.common.notes", ColumnDataType.STRING, true, 5, false, null);
        when(adminService.createColumnDef(eq("ADDRESS_LIST"), eq(req)))
                .thenReturn(new ColumnDefResponse(2L, "ADDRESS_LIST", "notes", "columns.common.notes",
                        ColumnDataType.STRING, true, 5, false, null));

        mockMvc.perform(post("/api/ui/screens/{screenCode}/columns", "ADDRESS_LIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "columnKey": "notes",
                                  "labelKey": "columns.common.notes",
                                  "dataType": "STRING",
                                  "defaultVisible": true,
                                  "defaultSequence": 5,
                                  "locked": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.columnKey").value("notes"));
    }

    @Test
    @DisplayName("DELETE /api/ui/screens/{code}/columns/{id} — kolon siler")
    void deleteColumn_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/ui/screens/{screenCode}/columns/{columnDefId}", "ADDRESS_LIST", 9L))
                .andExpect(status().isNoContent());

        verify(adminService).deleteColumnDef("ADDRESS_LIST", 9L);
    }
}

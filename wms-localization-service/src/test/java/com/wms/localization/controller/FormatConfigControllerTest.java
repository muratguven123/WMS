package com.wms.localization.controller;

import com.wms.localization.dto.ActiveFormatResponse;
import com.wms.localization.exception.handler.GlobalExceptionHandler;
import com.wms.localization.security.TenantContext;
import com.wms.localization.security.TenantContextHolder;
import com.wms.localization.service.FormatConfigService;
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


import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FormatConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("FormatConfigController")
class FormatConfigControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private FormatConfigService formatConfigService;

    private Long locationId;

    @BeforeEach
    void setUp() {
        locationId = 1L;
        TenantContextHolder.setContext(new TenantContext(
                locationId,
                1L,
                1L
        ));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("GET /api/v1/formats/active — aktif format konfigürasyonunu döner")
    void getActiveFormat_returnsConfig() throws Exception {
        ActiveFormatResponse response = ActiveFormatResponse.of(
                "dd.MM.yyyy",
                "HH:mm",
                ",",
                "."
        );

        when(formatConfigService.resolveActiveFormat(eq(locationId))).thenReturn(response);

        mockMvc.perform(get("/api/v1/formats/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateFormat").value("dd.MM.yyyy"))
                .andExpect(jsonPath("$.timeFormat").value("HH:mm"))
                .andExpect(jsonPath("$.decimalSeparator").value(","))
                .andExpect(jsonPath("$.thousandSeparator").value("."));
    }
}

package com.wms.localization.controller;

import com.wms.localization.exception.handler.GlobalExceptionHandler;
import com.wms.localization.service.TranslationExportService;
import com.wms.localization.service.TranslationImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TranslationImportExportController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("TranslationImportExportController")
class TranslationImportExportControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private TranslationExportService exportService;
    @MockBean private TranslationImportService importService;

    @Test
    @DisplayName("GET /api/v1/translations/export — Excel dosyası döner")
    void exportTranslations_returnsExcel() throws Exception {
        byte[] content = new byte[]{0x50, 0x4B, 0x03, 0x04};
        when(exportService.exportAsExcel("tr")).thenReturn(content);

        mockMvc.perform(get("/api/v1/translations/export")
                        .param("lang", "tr"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().exists("Content-Disposition"));
    }
}

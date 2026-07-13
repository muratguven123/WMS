package com.wms.localization.controller;

import com.wms.localization.entity.Language;
import com.wms.localization.exception.handler.GlobalExceptionHandler;
import com.wms.localization.service.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LanguageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("LanguageController")
class LanguageControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private LanguageService languageService;
    @MockBean private com.wms.localization.service.LanguageAutoTranslateService autoTranslateService;
    @MockBean private com.wms.localization.repository.TranslationValueRepository translationValueRepository;
    @MockBean private com.wms.localization.repository.TranslationKeyRepository translationKeyRepository;

    @Test
    @DisplayName("GET /api/v1/languages — aktif dilleri döner")
    void getActiveLanguages_returnsList() throws Exception {
        Language turkish = Language.builder()
                .id(1L)
                .code("tr")
                .name("Türkçe")
                .isDefault(true)
                .isActive(true)
                .build();

        when(languageService.getActiveLanguages()).thenReturn(List.of(turkish));

        mockMvc.perform(get("/api/v1/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("tr"))
                .andExpect(jsonPath("$[0].name").value("Türkçe"))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/languages/{code} — dili pasifleştirir")
    void deactivateLanguage_returnsUpdatedLanguage() throws Exception {
        Language serbian = Language.builder()
                .id(99L)
                .code("rs")
                .name("Srpski")
                .isDefault(false)
                .isActive(false)
                .build();

        when(languageService.deactivateLanguage("rs")).thenReturn(serbian);

        mockMvc.perform(delete("/api/v1/languages/rs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("rs"))
                .andExpect(jsonPath("$.isActive").value(false));
    }
}

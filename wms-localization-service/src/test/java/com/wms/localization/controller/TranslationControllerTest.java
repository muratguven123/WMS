package com.wms.localization.controller;

import com.wms.localization.exception.handler.GlobalExceptionHandler;
import com.wms.localization.service.TranslationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TranslationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("TranslationController")
class TranslationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private TranslationService translationService;

    @Test
    @DisplayName("GET /api/v1/translations — locale ve module ile çeviri paketi döner")
    void getTranslations_returnsMap() throws Exception {
        when(translationService.getTranslations("tr", "UI"))
                .thenReturn(Map.of(
                        "common.save", "Kaydet",
                        "common.cancel", "İptal"
                ));

        mockMvc.perform(get("/api/v1/translations")
                        .param("locale", "tr")
                        .param("module", "UI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['common.save']").value("Kaydet"))
                .andExpect(jsonPath("$.['common.cancel']").value("İptal"))
                .andExpect(header().string("Cache-Control", "max-age=3600, must-revalidate"));
    }
}

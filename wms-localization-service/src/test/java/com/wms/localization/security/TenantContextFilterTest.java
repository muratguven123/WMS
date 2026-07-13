package com.wms.localization.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextFilterTest {

    @Test
    @DisplayName("GET /api/v1/languages — tenant filtresinden muaf")
    void languagesList_skipsTenantContext() {
        assertThat(TenantContextFilter.skipsTenantContext("/api/v1/languages", "GET")).isTrue();
    }

    @Test
    @DisplayName("GET /api/v1/translations — tenant filtresinden muaf")
    void translations_skipsTenantContext() {
        assertThat(TenantContextFilter.skipsTenantContext("/api/v1/translations", "GET")).isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/languages — localization admin, tenant filtresinden muaf")
    void languagesPost_skipsTenantContext() {
        assertThat(TenantContextFilter.skipsTenantContext("/api/v1/languages", "POST")).isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/languages/{code}/auto-translate — localization admin, tenant filtresinden muaf")
    void autoTranslate_skipsTenantContext() {
        assertThat(TenantContextFilter.skipsTenantContext("/api/v1/languages/en/auto-translate", "POST")).isTrue();
    }

    @Test
    @DisplayName("DELETE /api/v1/languages/{code} — localization admin, tenant filtresinden muaf")
    void languagesDelete_skipsTenantContext() {
        assertThat(TenantContextFilter.skipsTenantContext("/api/v1/languages/rs", "DELETE")).isTrue();
    }

    @Test
    @DisplayName("POST /api/addresses — tenant filtresinden muaf")
    void addressesPost_skipsTenantContext() {
        assertThat(TenantContextFilter.skipsTenantContext("/api/addresses", "POST")).isTrue();
    }

    @Test
    @DisplayName("GET /api/v1/translations/export — tenant filtresi gerekli")
    void translationsExport_requiresTenantFilter() {
        assertThat(TenantContextFilter.skipsTenantContext("/api/v1/translations/export", "GET")).isFalse();
    }
}

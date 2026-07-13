package com.wms.integration.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WmsUserIdClaimResolverTest {

    @Test
    @DisplayName("Sayısal claim parse edilir")
    void numericClaim() {
        assertThat(WmsUserIdClaimResolver.resolveClaim("1")).isEqualTo(1L);
    }

    @Test
    @DisplayName("Legacy demo user UUID → 1L")
    void legacyDemoUuid() {
        assertThat(WmsUserIdClaimResolver.resolveClaim("55555555-0000-0000-0000-000000000001"))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Bilinmeyen UUID reddedilir")
    void unknownUuid() {
        assertThat(WmsUserIdClaimResolver.resolveClaim("99999999-9999-9999-9999-999999999999"))
                .isNull();
    }
}

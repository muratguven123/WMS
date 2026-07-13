package com.wms.finance.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WmsUserIdClaimResolverTest {

    @Test
    void numericClaim() {
        assertThat(WmsUserIdClaimResolver.resolveClaim("1")).isEqualTo(1L);
    }

    @Test
    void legacyDemoUuid() {
        assertThat(WmsUserIdClaimResolver.resolveClaim("55555555-0000-0000-0000-000000000001"))
                .isEqualTo(1L);
    }
}

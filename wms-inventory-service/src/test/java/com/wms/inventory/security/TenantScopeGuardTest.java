package com.wms.inventory.security;

import com.wms.inventory.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantScopeGuardTest {

    private static final Long TUZLA_ID = 1L;
    private static final Long BERLIN_ID = 2L;
    private static final Long COMPANY_TR = 1L;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void assertMatchesContext_rejectsBerlinIdWhenTuzlaActive() {
        TenantContextHolder.setContext(new TenantContext(1L, COMPANY_TR, TUZLA_ID));

        assertThatThrownBy(() -> TenantScopeGuard.assertMatchesContext(BERLIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void assertEntityBelongsToContext_rejectsCrossWarehouseResource() {
        TenantContextHolder.setContext(new TenantContext(1L, COMPANY_TR, TUZLA_ID));

        assertThatThrownBy(() -> TenantScopeGuard.assertEntityBelongsToContext(BERLIN_ID, COMPANY_TR))
                .isInstanceOf(BusinessException.class);
    }
}

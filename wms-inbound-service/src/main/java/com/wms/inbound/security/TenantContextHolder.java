package com.wms.inbound.security;

import java.util.Optional;

public final class TenantContextHolder {

    private static final InheritableThreadLocal<TenantContext> CONTEXT =
            new InheritableThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setContext(TenantContext context) {
        CONTEXT.set(context);
    }

    public static Optional<TenantContext> getContext() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static TenantContext require() {
        TenantContext ctx = CONTEXT.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "TenantContext is not initialized. Ensure the request passed through TenantContextFilter.");
        }
        return ctx;
    }

    public static Long getUserId() {
        return require().userId();
    }

    public static Long getCompanyId() {
        return require().companyId();
    }

    public static Long getLocationId() {
        return require().locationId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

package com.wms.core.security;

import java.util.Optional;

/**
 * İstek yaşam döngüsü boyunca aktif tenant bilgisini ThreadLocal'de saklayan utility sınıf.
 *
 * <p>InheritableThreadLocal kullanılır — @Async veya CompletableFuture ile
 * oluşturulan child thread'lere context otomatik aktarılır.</p>
 *
 * <p><b>Önemli:</b> Filter'ın finally bloğunda {@link #clear()} çağrılmalıdır.</p>
 */
public final class TenantContextHolder {

    private static final InheritableThreadLocal<TenantContext> CONTEXT =
            new InheritableThreadLocal<>();

    private TenantContextHolder() {
        // utility class — instance oluşturulamaz
    }

    /** Aktif context'i set eder. */
    public static void setContext(TenantContext context) {
        CONTEXT.set(context);
    }

    /** Aktif context'i döner, yoksa boş Optional. */
    public static Optional<TenantContext> getContext() {
        return Optional.ofNullable(CONTEXT.get());
    }

    /**
     * Aktif context'i döner, yoksa IllegalStateException fırlatır.
     * Service katmanında güvenle kullanılabilir — filter zaten set etmiş olmalı.
     */
    public static TenantContext require() {
        TenantContext ctx = CONTEXT.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "TenantContext is not initialized. Ensure the request passed through TenantContextFilter.");
        }
        return ctx;
    }

    /** Aktif kullanıcı ID'si — kısa erişim metodu. */
    public static Long getUserId() {
        return require().userId();
    }

    /** Aktif şirket ID'si — kısa erişim metodu. */
    public static Long getCompanyId() {
        return require().companyId();
    }

    /** Aktif lokasyon ID'si — kısa erişim metodu. */
    public static Long getLocationId() {
        return require().locationId();
    }

    /** ThreadLocal sızıntısını önlemek için context'i temizler. */
    public static void clear() {
        CONTEXT.remove();
    }
}

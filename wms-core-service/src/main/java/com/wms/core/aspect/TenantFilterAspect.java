package com.wms.core.aspect;

import com.wms.core.entity.BaseScopedEntity;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.security.annotation.IgnoreTenantFilter;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * AOP Aspect — Spring Data JPA Repository method çağrılarında
 * Hibernate tenant filtrelerini otomatik aktifleştirir.
 *
 * <p>Her repository çağrısından önce:</p>
 * <ol>
 *   <li>{@link TenantContextHolder}'dan aktif context okunur</li>
 *   <li>Hibernate Session üzerinde {@code companyFilter} ve {@code locationFilter} enable edilir</li>
 *   <li>Repository metodu çalıştırılır (filtreli SQL üretilir)</li>
 *   <li>After — filtreler disable edilir (diğer non-scoped sorgular etkilenmesin)</li>
 * </ol>
 *
 * <p>{@link IgnoreTenantFilter} annotation'ı ile bypass edilebilir.</p>
 */
@Aspect
@Component
public class TenantFilterAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantFilterAspect.class);

    private final EntityManager entityManager;

    public TenantFilterAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Tüm Spring Data JPA Repository method'larını intercept eder.
     * execution pattern: com.wms.core.repository paketindeki tüm public method'lar.
     */
    @Around("execution(* com.wms.core.repository..*(..))")
    public Object applyTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {

        // 1. @IgnoreTenantFilter kontrolü
        if (shouldIgnore(joinPoint)) {
            log.debug("Tenant filter bypassed for: {}", joinPoint.getSignature().toShortString());
            return joinPoint.proceed();
        }

        // 2. TenantContext mevcut mu?
        Optional<TenantContext> ctxOpt = TenantContextHolder.getContext();
        if (ctxOpt.isEmpty()) {
            // Context yoksa filtre uygulamadan devam et
            // (startup, migration, seed gibi context-dışı senaryolar)
            return joinPoint.proceed();
        }

        TenantContext ctx = ctxOpt.get();
        Session session = entityManager.unwrap(Session.class);

        try {
            // 3. Filtreleri aktifleştir
            enableFilters(session, ctx);

            // 4. Repository metodunu çalıştır
            return joinPoint.proceed();

        } finally {
            // 5. Filtreleri deaktif et — sonraki sorgular etkilenmesin
            disableFilters(session);
        }
    }

    private void enableFilters(Session session, TenantContext ctx) {
        Filter companyFilter = session.enableFilter(BaseScopedEntity.COMPANY_FILTER);
        companyFilter.setParameter("companyId", ctx.companyId());

        Filter locationFilter = session.enableFilter(BaseScopedEntity.LOCATION_FILTER);
        locationFilter.setParameter("locationId", ctx.locationId());

        log.debug("Tenant filters enabled — companyId={}, locationId={}",
                ctx.companyId(), ctx.locationId());
    }

    private void disableFilters(Session session) {
        session.disableFilter(BaseScopedEntity.COMPANY_FILTER);
        session.disableFilter(BaseScopedEntity.LOCATION_FILTER);
    }

    /**
     * Method veya sınıf düzeyinde @IgnoreTenantFilter var mı kontrol eder.
     */
    private boolean shouldIgnore(ProceedingJoinPoint joinPoint) {
        // Method düzeyinde kontrol
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (method.isAnnotationPresent(IgnoreTenantFilter.class)) {
            return true;
        }

        // Sınıf düzeyinde kontrol
        Class<?> targetClass = joinPoint.getTarget().getClass();
        return targetClass.isAnnotationPresent(IgnoreTenantFilter.class);
    }
}

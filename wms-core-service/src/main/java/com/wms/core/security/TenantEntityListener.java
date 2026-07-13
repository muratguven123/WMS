package com.wms.core.security;

import com.wms.core.entity.BaseScopedEntity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA Entity Lifecycle Listener — {@link BaseScopedEntity} türevlerinde
 * {@code companyId} ve {@code locationId} alanlarını {@link TenantContextHolder}'dan
 * otomatik doldurur.
 *
 * <p>Geliştirici bu alanları elle set etmek zorunda kalmaz.
 * Context yoksa (örn: batch/seed job) IllegalStateException fırlatılır.</p>
 */
public class TenantEntityListener {

    private static final Logger log = LoggerFactory.getLogger(TenantEntityListener.class);

    /**
     * Yeni kayıt oluşturulurken companyId ve locationId otomatik set edilir.
     */
    @PrePersist
    public void prePersist(BaseScopedEntity entity) {
        TenantContext ctx = TenantContextHolder.require();

        if (entity.getCompanyId() == null) {
            entity.setCompanyId(ctx.companyId());
        }
        if (entity.getLocationId() == null) {
            entity.setLocationId(ctx.locationId());
        }

        log.debug("PrePersist — companyId={}, locationId={} set for {}",
                entity.getCompanyId(), entity.getLocationId(),
                entity.getClass().getSimpleName());
    }

    /**
     * Güncelleme sırasında tenant alanlarının değiştirilmediğini doğrular.
     * companyId ve locationId updatable=false olduğundan JPA zaten engelleyecektir,
     * ancak bu ekstra bir savunma katmanıdır.
     */
    @PreUpdate
    public void preUpdate(BaseScopedEntity entity) {
        TenantContext ctx = TenantContextHolder.require();

        if (!ctx.companyId().equals(entity.getCompanyId())) {
            throw new SecurityException(
                    "Tenant companyId mismatch on update. Entity companyId="
                    + entity.getCompanyId() + ", context companyId=" + ctx.companyId());
        }
        if (!ctx.locationId().equals(entity.getLocationId())) {
            throw new SecurityException(
                    "Tenant locationId mismatch on update. Entity locationId="
                    + entity.getLocationId() + ", context locationId=" + ctx.locationId());
        }
    }
}

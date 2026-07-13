package com.wms.core.entity;

import com.wms.core.security.TenantEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Tenant-scoped operasyonel entity'ler için base sınıf.
 *
 * <p>Stock, Order, Shipment gibi iş verisi taşıyan tüm entity'ler bu sınıftan türer.
 * BaseEntity'den gelen BIGINT PK, soft delete ve audit alanlarına ek olarak
 * {@code companyId} ve {@code locationId} alanlarını zorunlu kılar.</p>
 *
 * <h3>Hibernate Filter mekanizması</h3>
 * <ul>
 *   <li>{@code companyFilter} — SELECT sorgularına {@code company_id = :companyId} koşulunu ekler</li>
 *   <li>{@code locationFilter} — SELECT sorgularına {@code location_id = :locationId} koşulunu ekler</li>
 * </ul>
 *
 * <p>Filtreler {@link com.wms.core.aspect.TenantFilterAspect} tarafından
 * her repository çağrısında otomatik aktifleştirilir.</p>
 *
 * <h3>Auto-fill</h3>
 * <p>{@link TenantEntityListener} ile {@code @PrePersist} / {@code @PreUpdate}
 * aşamasında companyId ve locationId otomatik doldurulur.</p>
 */
@Getter
@Setter
@MappedSuperclass
@FilterDef(name = BaseScopedEntity.COMPANY_FILTER,
           parameters = @ParamDef(name = "companyId", type = Long.class))
@FilterDef(name = BaseScopedEntity.LOCATION_FILTER,
           parameters = @ParamDef(name = "locationId", type = Long.class))
@Filter(name = BaseScopedEntity.COMPANY_FILTER,  condition = "company_id = :companyId")
@Filter(name = BaseScopedEntity.LOCATION_FILTER, condition = "location_id = :locationId")
@EntityListeners(TenantEntityListener.class)
public abstract class BaseScopedEntity extends BaseEntity {

    public static final String COMPANY_FILTER  = "companyFilter";
    public static final String LOCATION_FILTER = "locationFilter";

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "location_id", nullable = false, updatable = false)
    private Long locationId;
}

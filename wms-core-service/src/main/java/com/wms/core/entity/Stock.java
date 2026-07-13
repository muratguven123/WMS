package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Lokasyon bazlı stok kaydı — tenant-scoped operasyonel entity örneği.
 *
 * <p>{@link BaseScopedEntity} üzerinden Hibernate filtreleri ve
 * {@link com.wms.core.security.TenantEntityListener} ile otomatik companyId/locationId
 * doldurma mekanizmasını gösterir.</p>
 */
@Entity
@Table(name = "stocks",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_stock_location_sku",
           columnNames = {"location_id", "sku"}))
@SQLDelete(sql = "UPDATE stocks SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock extends BaseScopedEntity {

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "quantity", nullable = false)
    private int quantity;
}

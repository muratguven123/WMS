package com.wms.core.repository;

import com.wms.core.entity.Stock;
import com.wms.core.security.annotation.IgnoreTenantFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    List<Stock> findBySkuContainingIgnoreCase(String sku);

    Optional<Stock> findBySku(String sku);

    /**
     * Tenant filtrelerini BYPASS ederek tüm şirket/lokasyon kayıtlarını döner.
     * Yalnızca admin/raporlama senaryoları için — {@link IgnoreTenantFilter} sayesinde
     * {@code TenantFilterAspect} bu çağrıda Hibernate filtrelerini aktifleştirmez.
     */
    @IgnoreTenantFilter
    @Query("SELECT s FROM Stock s")
    List<Stock> findAllBypassingTenantFilter();
}

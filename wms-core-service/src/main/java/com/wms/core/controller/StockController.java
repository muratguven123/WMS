package com.wms.core.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.core.entity.Stock;
import com.wms.core.repository.StockRepository;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Tenant-scoped stok sorgulama — {@link TenantContextHolder} ve Hibernate filtrelerini gösterir.
 *
 * <p>Bu noktaya ulaşan her istek TenantContextFilter'dan geçmiştir;
 * StockRepository çağrıları TenantFilterAspect tarafından otomatik filtrelenir.</p>
 */
@RestController
@RequestMapping("/api/stocks")
@PreAuthorize("hasAnyRole('INVENTORY_CLERK', 'WAREHOUSE_MANAGER', 'WMS_ADMIN')")
public class StockController {

    private final StockRepository stockRepository;

    public StockController(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStocks() {
        TenantContext ctx = TenantContextHolder.require();

        List<Stock> stocks = stockRepository.findAll();

        Map<String, Object> response = Map.of(
                "companyId",  ctx.companyId(),
                "locationId", ctx.locationId(),
                "userId",     ctx.userId(),
                "count",      stocks.size(),
                "stocks",     stocks.stream()
                        .map(s -> Map.of("id", s.getId(), "sku", s.getSku(), "quantity", s.getQuantity()))
                        .toList()
        );

        return ResponseEntity.ok(response);
    }
}

package com.wms.integration.adapter;

import com.wms.integration.entity.LocationIntegrationConfig;
import com.wms.integration.repository.LocationIntegrationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;


/**
 * Lokasyon ID'ye göre doğru {@link ErpAdapter} implementasyonunu dinamik olarak
 * çözen Spring bean fabrikası.
 *
 * <h3>Çalışma Mantığı</h3>
 * <ol>
 *   <li>{@link LocationIntegrationConfigRepository}'den lokasyon için aktif
 *       konfigürasyon çekilir; bu kayıt ilgili {@link com.wms.integration.entity.IntegrationSystem}
 *       ile JOIN FETCH edilmiş gelir.</li>
 *   <li>{@code IntegrationSystem.code} değeri küçük harfe çevrilerek {@code "Adapter"}
 *       eki eklenir → Spring bean adı oluşur
 *       (örn: {@code "SAP"} → {@code "sapAdapter"}).</li>
 *   <li>{@link ApplicationContext#getBean} ile bean çözümlenir; singleton
 *       olduğundan aynı instance döner.</li>
 * </ol>
 *
 * <h3>Yeni ERP Ekleme</h3>
 * Sadece {@link ErpAdapter} implementasyonu yazıp
 * {@code @Component("oracleAdapter")} isimlendirmesi yeterlidir; factory
 * değişmez (Open/Closed Principle).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpAdapterFactory {

    private final ApplicationContext applicationContext;
    private final LocationIntegrationConfigRepository configRepository;

    /**
     * Lokasyon Long'si için aktif ERP adaptörünü döner.
     *
     * @param locationId depo/lokasyon Long
     * @return ilgili {@link ErpAdapter} implementasyonu
     * @throws ErpAdapterNotFoundException aktif konfigürasyon yoksa veya bean bulunamazsa
     */
    public ErpAdapter getAdapter(Long locationId) {
        LocationIntegrationConfig config = configRepository
                .findActiveByLocationId(locationId)
                .orElseThrow(() -> new ErpAdapterNotFoundException(
                        "No active ERP integration config found for locationId=" + locationId));

        String erpCode  = config.getIntegrationSystem().getCode();
        String beanName = resolveBeanName(erpCode);

        log.debug("[ErpAdapterFactory] locationId={} -> erpCode={} -> bean={}",
                locationId, erpCode, beanName);

        try {
            return applicationContext.getBean(beanName, ErpAdapter.class);
        } catch (Exception ex) {
            throw new ErpAdapterNotFoundException(
                    "No ErpAdapter bean found with name '" + beanName
                    + "' for erpCode='" + erpCode + "'", ex);
        }
    }

    /**
     * ERP kod string'ini Spring bean adına çevirir.
     *
     * <pre>
     *   "SAP"    → "sapAdapter"
     *   "LOGO"   → "logoAdapter"
     *   "ORACLE" → "oracleAdapter"
     *   "MOCK"   → "mockAdapter"
     * </pre>
     */
    private String resolveBeanName(String erpCode) {
        return erpCode.toLowerCase() + "Adapter";
    }

    // -----------------------------------------------------------------------
    // Inner exception
    // -----------------------------------------------------------------------

    public static class ErpAdapterNotFoundException extends RuntimeException {
        public ErpAdapterNotFoundException(String message) {
            super(message);
        }
        public ErpAdapterNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

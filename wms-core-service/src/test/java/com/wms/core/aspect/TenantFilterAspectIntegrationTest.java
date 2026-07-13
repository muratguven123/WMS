package com.wms.core.aspect;

import com.wms.core.entity.Company;
import com.wms.core.entity.Country;
import com.wms.core.entity.Location;
import com.wms.core.entity.Organization;
import com.wms.core.entity.Region;
import com.wms.core.entity.Stock;
import com.wms.core.entity.enums.LocationType;
import com.wms.core.repository.StockRepository;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link TenantFilterAspect} multi-tenant veri izolasyonu entegrasyon testi.
 *
 * <h3>Test altyapısı</h3>
 * <ul>
 *   <li><b>@DataJpaTest</b> — JPA slice; repository'ler, EntityManager ve
 *       (slice'a dahil olan AopAutoConfiguration sayesinde) AOP proxy'leri yüklenir.</li>
 *   <li><b>Testcontainers PostgreSQL</b> — şema TIMESTAMPTZ, JSONB ve
 *       {@code gen_random_uuid()} içerdiğinden H2 yerine production-parity
 *       gerçek PostgreSQL kullanılır. Flyway migration'ları (V1..Vn) container
 *       üzerinde otomatik koşar; böylece test, üretim şemasının birebir aynısında çalışır.</li>
 *   <li><b>@Import(TenantFilterAspect)</b> — slice component-scan yapmadığı için
 *       aspect bean'i elle dahil edilir; repository proxy'lerine otomatik örülür.</li>
 * </ul>
 *
 * <h3>Doğrulanan davranışlar</h3>
 * <ol>
 *   <li>Context (CompanyA/LocationA) set edildiğinde findAll() yalnızca A verisini döner</li>
 *   <li>Context CompanyB/LocationB'ye çevrildiğinde aynı repository yalnızca B verisini döner</li>
 *   <li>{@code @IgnoreTenantFilter} metodu, context set olsa bile TÜM veriyi döner</li>
 *   <li>Context yokken filtreler pasif kalır; hata fırlatılmaz, tüm veri okunur</li>
 * </ol>
 *
 * <p>Not: Testler Docker gerektirir (Testcontainers).</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({TenantFilterAspect.class, AopAutoConfiguration.class})
@DisplayName("TenantFilterAspect — multi-tenant veri izolasyonu")
class TenantFilterAspectIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Long TEST_USER_ID = 1L;

    private static final String SKU_A1 = "TENANT-A-SKU-1";
    private static final String SKU_A2 = "TENANT-A-SKU-2";
    private static final String SKU_B1 = "TENANT-B-SKU-1";

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long companyAId;
    private Long locationAId;
    private Long companyBId;
    private Long locationBId;

    // =================================================================
    // Fixture — her test öncesi iki ayrı tenant için veri hazırlanır
    // =================================================================

    @BeforeEach
    void seedTwoTenants() {
        // --- Ortak master data (FK zinciri: Country -> Region, Organization -> Company -> Location)
        Country country = entityManager.persist(
                Country.builder().isoCode("ZZ").name("Testland").build());
        Region region = entityManager.persist(
                Region.builder().country(country).name("Test Region").build());
        Organization org = entityManager.persist(
                Organization.builder().name("Test Org").build());

        Company companyA = entityManager.persist(Company.builder()
                .organization(org).name("Company A").taxNumber("TAX-A-0001").build());
        Company companyB = entityManager.persist(Company.builder()
                .organization(org).name("Company B").taxNumber("TAX-B-0001").build());

        Location locationA = entityManager.persist(Location.builder()
                .company(companyA).region(region).name("Warehouse A")
                .type(LocationType.CENTRAL).timezone("Europe/Istanbul").build());
        Location locationB = entityManager.persist(Location.builder()
                .company(companyB).region(region).name("Warehouse B")
                .type(LocationType.CENTRAL).timezone("Europe/Berlin").build());
        entityManager.flush();

        companyAId  = companyA.getId();
        locationAId = locationA.getId();
        companyBId  = companyB.getId();
        locationBId = locationB.getId();

        // --- Tenant A verisi: TenantEntityListener companyId/locationId'yi
        //     aktif context'ten otomatik doldurur (@PrePersist)
        switchTenant(companyAId, locationAId);
        entityManager.persist(Stock.builder().sku(SKU_A1).quantity(10).build());
        entityManager.persist(Stock.builder().sku(SKU_A2).quantity(20).build());
        entityManager.flush();

        // --- Tenant B verisi
        switchTenant(companyBId, locationBId);
        entityManager.persist(Stock.builder().sku(SKU_B1).quantity(30).build());
        entityManager.flush();

        entityManager.clear();          // L1 cache'i boşalt — sorgular DB'ye insin
        TenantContextHolder.clear();    // her test kendi context'ini kurar
    }

    @AfterEach
    void cleanContext() {
        // ThreadLocal sızıntısını önle — testler aynı thread'i paylaşabilir
        TenantContextHolder.clear();
    }

    // =================================================================
    // Senaryo 1 — Context set edildiğinde filtreleme
    // =================================================================

    @Test
    @DisplayName("Context CompanyA/LocationA iken findAll() yalnızca A kayıtlarını döner")
    void whenTenantContextIsSet_findAllReturnsOnlyThatTenantsData() {
        switchTenant(companyAId, locationAId);

        List<Stock> result = stockRepository.findAll();

        assertThat(result)
                .extracting(Stock::getSku)
                .containsExactlyInAnyOrder(SKU_A1, SKU_A2);
        assertThat(result)
                .allSatisfy(stock -> {
                    assertThat(stock.getCompanyId()).isEqualTo(companyAId);
                    assertThat(stock.getLocationId()).isEqualTo(locationAId);
                });
    }

    // =================================================================
    // Senaryo 2 — Context değiştirildiğinde filtreleme
    // =================================================================

    @Test
    @DisplayName("Context CompanyB/LocationB'ye çevrilince aynı repository yalnızca B kayıtlarını döner")
    void whenTenantContextSwitches_findAllReturnsOnlyNewTenantsData() {
        // Önce A — baseline doğrulama
        switchTenant(companyAId, locationAId);
        assertThat(stockRepository.findAll())
                .extracting(Stock::getSku)
                .containsExactlyInAnyOrder(SKU_A1, SKU_A2);

        // Context B'ye geçir — aynı repository, aynı transaction
        switchTenant(companyBId, locationBId);

        List<Stock> result = stockRepository.findAll();

        assertThat(result)
                .extracting(Stock::getSku)
                .containsExactly(SKU_B1);
        assertThat(result)
                .allSatisfy(stock -> {
                    assertThat(stock.getCompanyId()).isEqualTo(companyBId);
                    assertThat(stock.getLocationId()).isEqualTo(locationBId);
                });
    }

    // =================================================================
    // Senaryo 3 — @IgnoreTenantFilter bypass
    // =================================================================

    @Test
    @DisplayName("@IgnoreTenantFilter metodu, context set olsa bile tüm tenant kayıtlarını döner")
    void whenMethodHasIgnoreTenantFilter_allTenantsDataIsReturned() {
        switchTenant(companyAId, locationAId);

        List<Stock> result = stockRepository.findAllBypassingTenantFilter();

        // Hem A hem B kayıtları görünmeli (migration seed'lerine karşı
        // dayanıklılık için 'contains' — exact değil)
        assertThat(result)
                .extracting(Stock::getSku)
                .contains(SKU_A1, SKU_A2, SKU_B1);
        assertThat(result)
                .extracting(Stock::getCompanyId)
                .contains(companyAId, companyBId);
    }

    // =================================================================
    // Senaryo 4 — Context yokken davranış
    // =================================================================

    @Test
    @DisplayName("Context temizlendiğinde filtreler pasif kalır — hatasız, tüm veri döner")
    void whenNoTenantContext_filtersAreInactiveAndAllDataIsReturned() {
        TenantContextHolder.clear();

        assertThatCode(() -> stockRepository.findAll()).doesNotThrowAnyException();

        assertThat(stockRepository.findAll())
                .extracting(Stock::getSku)
                .contains(SKU_A1, SKU_A2, SKU_B1);
    }

    // =================================================================
    // Yardımcılar
    // =================================================================

    private void switchTenant(Long companyId, Long locationId) {
        TenantContextHolder.setContext(new TenantContext(TEST_USER_ID, companyId, locationId));
    }
}

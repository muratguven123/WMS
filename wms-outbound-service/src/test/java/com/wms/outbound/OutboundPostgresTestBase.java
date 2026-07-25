package com.wms.outbound;

import com.wms.testsupport.MigrationChainSupport;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.LinkedHashMap;

/**
 * Outbound entegrasyon/repository testleri için ortak taban.
 *
 * <p>Gerçek PostgreSQL 16 (Testcontainers) üzerinde dblink zinciri
 * (core &rarr; finance &rarr; localization &rarr; outbound) bir kez migrate edilir;
 * JPA bu migrate edilmiş {@code wms_outbound_db}'ye bağlanır. Flyway test tarafında
 * kapatılır (şema zaten kurulu), Hibernate şemaya dokunmaz ({@code ddl-auto=none}).
 * Böylece test, üretim şemasının birebir aynısında çalışır — H2'nin taklit edemediği
 * JSONB/TIMESTAMPTZ/partial index davranışları dahil.
 *
 * <p>Container ve migration statik blokta bir kez kurulur; bu yüzden
 * {@code @DynamicPropertySource} çözümlenirken JDBC URL hazırdır. Testler bu sınıfı
 * extend eder ve kendi test-tipi anotasyonunu ({@code @SpringBootTest}) taşır.
 *
 * <p>Docker gerektirir.
 */
public abstract class OutboundPostgresTestBase {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUsername("postgres")
            .withPassword("postgres");

    static final String JDBC_URL;

    static {
        POSTGRES.start();
        try {
            LinkedHashMap<String, String> chain = new LinkedHashMap<>();
            chain.put("wms_core_db", MigrationChainSupport.moduleMigrations("wms-core-service"));
            chain.put("wms_finance_db", MigrationChainSupport.moduleMigrations("wms-finance-service"));
            chain.put("wms_localization_db", MigrationChainSupport.moduleMigrations("wms-localization-service"));
            chain.put("wms_outbound_db", "classpath:db/migration");
            JDBC_URL = MigrationChainSupport.migrateChain(POSTGRES, chain);
        } catch (SQLException e) {
            throw new IllegalStateException("Outbound migration zinciri kurulamadı", e);
        }
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }
}

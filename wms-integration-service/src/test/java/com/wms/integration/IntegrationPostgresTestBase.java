package com.wms.integration;

import com.wms.testsupport.MigrationChainSupport;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.LinkedHashMap;

/**
 * Integration entegrasyon/repository testleri için ortak taban.
 *
 * <p>Gerçek PostgreSQL 16 (Testcontainers) üzerinde dblink zinciri (core &rarr; integration)
 * bir kez migrate edilir; JPA bu migrate edilmiş {@code wms_integration_db}'ye bağlanır.
 * Flyway test tarafında kapatılır (şema zaten kurulu), Hibernate şemaya dokunmaz
 * ({@code ddl-auto=none}). Böylece test, üretim şemasının birebir aynısında çalışır —
 * H2'nin taklit edemediği TIMESTAMPTZ/partial index/enum davranışları dahil.
 *
 * <p>Container ve migration statik blokta bir kez kurulur; {@code @DynamicPropertySource}
 * çözümlenirken JDBC URL hazırdır. Testler bu sınıfı extend eder ve kendi test-tipi
 * anotasyonunu (ör. {@code @DataJpaTest}) taşır.
 *
 * <p>Docker gerektirir.
 */
public abstract class IntegrationPostgresTestBase {

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
            chain.put("wms_integration_db", "classpath:db/migration");
            JDBC_URL = MigrationChainSupport.migrateChain(POSTGRES, chain);
        } catch (SQLException e) {
            throw new IllegalStateException("Integration migration zinciri kurulamadı", e);
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

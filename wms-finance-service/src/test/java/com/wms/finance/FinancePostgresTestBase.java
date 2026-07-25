package com.wms.finance;

import com.wms.testsupport.MigrationChainSupport;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.LinkedHashMap;

/**
 * Finance repository/entegrasyon testleri için ortak taban.
 *
 * <p>Gerçek PostgreSQL 16 (Testcontainers) üzerinde dblink zinciri (core &rarr; finance)
 * bir kez migrate edilir; JPA migrate edilmiş {@code wms_finance_db}'ye bağlanır.
 * Flyway kapatılır, Hibernate şemaya dokunmaz ({@code ddl-auto=none}). Docker gerektirir.
 */
public abstract class FinancePostgresTestBase {

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
            chain.put("wms_finance_db", "classpath:db/migration");
            JDBC_URL = MigrationChainSupport.migrateChain(POSTGRES, chain);
        } catch (SQLException e) {
            throw new IllegalStateException("Finance migration zinciri kurulamadı", e);
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

package com.wms.billing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Billing Flyway migration'larını gerçek PostgreSQL 16 üzerinde uygular.
 * dblink zinciri: core → finance → billing. Docker gerektirir.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Billing migration smoke")
class BillingMigrationSmokeIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUsername("postgres")
            .withPassword("postgres");

    private String jdbcUrl;

    @BeforeAll
    void migrateChain() throws SQLException {
        LinkedHashMap<String, String> chain = new LinkedHashMap<>();
        chain.put("wms_core_db", MigrationSmokeSupport.moduleMigrations("wms-core-service"));
        chain.put("wms_finance_db", MigrationSmokeSupport.moduleMigrations("wms-finance-service"));
        chain.put("wms_billing_db", "classpath:db/migration");
        jdbcUrl = MigrationSmokeSupport.migrateChain(postgres, chain);
    }

    @Test
    @DisplayName("Tüm migration'lar başarıyla uygulanır")
    void allMigrationsSucceed() throws SQLException {
        try (Connection conn = connect();
             ResultSet rs = conn.createStatement().executeQuery(
                     "SELECT COUNT(*) FILTER (WHERE success), COUNT(*) FILTER (WHERE NOT success)"
                             + " FROM flyway_schema_history")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("başarılı migration sayısı").isGreaterThan(0);
            assertThat(rs.getInt(2)).as("başarısız migration sayısı").isZero();
        }
    }

    @Test
    @DisplayName("Çekirdek tablo (invoices) şemada mevcut")
    void keyTableExists() throws SQLException {
        try (Connection conn = connect();
             ResultSet rs = conn.createStatement().executeQuery("SELECT to_regclass('public.invoices')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isNotNull();
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
    }
}

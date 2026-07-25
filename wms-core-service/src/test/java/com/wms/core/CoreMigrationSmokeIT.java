package com.wms.core;

import com.wms.testsupport.MigrationChainSupport;
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
 * Core Flyway migration'larını gerçek PostgreSQL 16 (Testcontainers) üzerinde uygular.
 *
 * <p>Core zincirin köküdür (upstream dblink bağımlılığı yoktur); bu yüzden tek DB
 * {@code wms_core_db} üzerinde tüm göçler uygulanır. Şema TIMESTAMPTZ / JSONB /
 * gen_random_uuid() ve BIGINT'e geçiş (V16) içerdiğinden H2 yerine üretim-eşi PostgreSQL
 * kullanılır. Bu test, downstream servislerin (inbound/inventory/outbound/billing…) dblink
 * zinciriyle bağlandığı core şemasının bütünlüğünü garanti eder. Docker gerektirir.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Core migration smoke")
class CoreMigrationSmokeIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUsername("postgres")
            .withPassword("postgres");

    private String jdbcUrl;

    @BeforeAll
    void migrate() throws SQLException {
        LinkedHashMap<String, String> chain = new LinkedHashMap<>();
        chain.put("wms_core_db", "classpath:db/migration");
        jdbcUrl = MigrationChainSupport.migrateChain(postgres, chain);
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
    @DisplayName("Organizasyon çekirdek tabloları şemada mevcut")
    void coreTablesExist() throws SQLException {
        assertThat(regclass("public.organizations")).isNotNull();
        assertThat(regclass("public.companies")).isNotNull();
        assertThat(regclass("public.locations")).isNotNull();
    }

    @Test
    @DisplayName("Dinamik UI şeması (V10) şemada mevcut")
    void dynamicUiTablesExist() throws SQLException {
        assertThat(regclass("public.screens")).isNotNull();
        assertThat(regclass("public.screen_fields")).isNotNull();
    }

    @Test
    @DisplayName("BIGINT geçişi (V16) — legacy id eşleme tablosu mevcut")
    void bigintMigrationApplied() throws SQLException {
        assertThat(regclass("public.id_legacy_map")).isNotNull();
    }

    private String regclass(String table) throws SQLException {
        try (Connection conn = connect();
             ResultSet rs = conn.createStatement().executeQuery("SELECT to_regclass('" + table + "')")) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
    }
}

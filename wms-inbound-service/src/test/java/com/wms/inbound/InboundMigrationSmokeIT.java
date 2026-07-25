package com.wms.inbound;

import com.wms.testsupport.MigrationChainSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
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
 * Inbound Flyway migration'larını gerçek PostgreSQL 16 (Testcontainers) üzerinde uygular.
 *
 * <p>dblink zinciri: core &rarr; inbound. Inbound {@code V4__uuid_to_bigint.sql}, core'un
 * {@code V16__uuid_to_bigint.sql} göçünün önce uygulanmış olmasını gerektirdiği için zincir
 * core &rarr; inbound sırasıyla kurulur. H2'nin taklit edemediği dblink/JSONB/TIMESTAMPTZ
 * davranışları üretim şemasının birebir aynısında doğrulanır. Docker yoksa atlanır.
 */
@EnabledIf("com.wms.testsupport.DockerSupport#isAvailable")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Inbound migration smoke")
class InboundMigrationSmokeIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUsername("postgres")
            .withPassword("postgres");

    private String jdbcUrl;

    @BeforeAll
    void migrateChain() throws SQLException {
        LinkedHashMap<String, String> chain = new LinkedHashMap<>();
        chain.put("wms_core_db", MigrationChainSupport.moduleMigrations("wms-core-service"));
        chain.put("wms_inbound_db", "classpath:db/migration");
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
    @DisplayName("Çekirdek tablolar (inbound_orders, receipts) şemada mevcut")
    void keyTablesExist() throws SQLException {
        assertThat(regclass("public.inbound_orders")).isNotNull();
        assertThat(regclass("public.receipts")).isNotNull();
        assertThat(regclass("public.receipt_items")).isNotNull();
    }

    @Test
    @DisplayName("Outbox tablosu (V2) şemada mevcut")
    void outboxTableExists() throws SQLException {
        assertThat(regclass("public.outbox_messages")).isNotNull();
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

package com.wms.localization.service.address;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Core + localization Flyway migration zincirini gerçek PostgreSQL üzerinde
 * doğrular (dblink dahil). Docker gerektirir.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Address template seed migrations")
class AddressTemplateSeedMigrationIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUsername("postgres")
            .withPassword("postgres");

    private AddressTemplateMigrationTestSupport.MigrationUrls urls;

    @BeforeAll
    void migrateBothDatabases() throws SQLException {
        urls = AddressTemplateMigrationTestSupport.setupAndMigrate(postgres);
    }

    @Test
    @DisplayName("V20 — cascade alanları TEXT (elle giriş), zip_code FIXED")
    void fieldMetadataBackfill() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                urls.locJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            assertFieldType(conn, "state", "TEXT", "NONE", null);
            assertFieldType(conn, "city", "TEXT", "NONE", null);
            assertFieldType(conn, "district", "TEXT", "NONE", null);
            assertFieldType(conn, "neighborhood", "TEXT", "NONE", null);
            assertFieldType(conn, "zip_code", "FIXED", "NONE", null);
        }
    }

    @Test
    @DisplayName("V18 + V16 — en az 15 ülke için şablon satırı seed edilmiş")
    void seedCoversFifteenCountries() throws SQLException {
        Map<String, Long> isoToId = loadCoreCountryIds();
        assertThat(isoToId.keySet()).containsAll(AddressTemplateSeedExpectations.supportedIsoCodes());

        try (Connection conn = DriverManager.getConnection(
                urls.locJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            for (String iso : AddressTemplateSeedExpectations.supportedIsoCodes()) {
                long countryId = isoToId.get(iso);
                int count = countTemplateRows(conn, countryId);
                assertThat(count)
                        .as("template rows for %s", iso)
                        .isGreaterThan(0);
            }
        }
    }

    @Test
    @DisplayName("V17 — US state mandatory satırı mevcut")
    void usStateTemplatePresent() throws SQLException {
        Map<String, Long> isoToId = loadCoreCountryIds();
        Long usId = isoToId.get("US");
        assertThat(usId).isNotNull();

        try (Connection conn = DriverManager.getConnection(
                urls.locJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT cat.is_mandatory, cat.sequence
                     FROM localization.country_address_template cat
                     JOIN localization.address_template_field f ON f.id = cat.address_template_field_id
                     WHERE cat.country_id = ? AND f.field_key = 'state'
                     """)) {
            ps.setLong(1, usId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("is_mandatory")).isTrue();
                assertThat(rs.getInt("sequence")).isZero();
            }
        }
    }

    private static void assertFieldType(
            Connection conn, String fieldKey, String fieldType, String masterSource, String parentKey
    ) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT field_type, master_data_source, parent_field_key
                FROM localization.address_template_field
                WHERE field_key = ?
                """)) {
            ps.setString(1, fieldKey);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("field_type")).isEqualTo(fieldType);
                assertThat(rs.getString("master_data_source")).isEqualTo(masterSource);
                if (parentKey == null) {
                    assertThat(rs.getString("parent_field_key")).isNull();
                } else {
                    assertThat(rs.getString("parent_field_key")).isEqualTo(parentKey);
                }
            }
        }
    }

    private Map<String, Long> loadCoreCountryIds() throws SQLException {
        Map<String, Long> map = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(
                urls.coreJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             PreparedStatement ps = conn.prepareStatement("SELECT id, iso_code FROM countries");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("iso_code"), rs.getLong("id"));
            }
        }
        return map;
    }

    private static int countTemplateRows(Connection conn, long countryId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM localization.country_address_template WHERE country_id = ?")) {
            ps.setLong(1, countryId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}

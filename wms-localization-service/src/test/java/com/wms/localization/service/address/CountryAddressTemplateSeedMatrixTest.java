package com.wms.localization.service.address;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3 §4 — 15 ülke şablon seed'inin sequence/mandatory/regex beklentileriyle
 * eşleştiğini doğrular.
 */
@Testcontainers
@DisplayName("CountryAddressTemplate seed matrix")
class CountryAddressTemplateSeedMatrixTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUsername("postgres")
            .withPassword("postgres");

    private static AddressTemplateMigrationTestSupport.MigrationUrls urls;
    private static Map<String, Long> isoToCountryId;

    @BeforeAll
    static void migrateAndLoadIds() throws SQLException {
        urls = AddressTemplateMigrationTestSupport.setupAndMigrate(postgres);

        isoToCountryId = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(
                urls.coreJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             PreparedStatement ps = conn.prepareStatement("SELECT id, iso_code FROM countries");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                isoToCountryId.put(rs.getString("iso_code"), rs.getLong("id"));
            }
        }
    }

    static Stream<AddressTemplateSeedExpectations.ExpectedRow> expectedRows() {
        return AddressTemplateSeedExpectations.allRows();
    }

    @ParameterizedTest(name = "{0} / {1} seq={3} mandatory={2}")
    @MethodSource("expectedRows")
    @DisplayName("şablon satırı matrisle eşleşmeli")
    void templateRowMatchesMatrix(AddressTemplateSeedExpectations.ExpectedRow expected) throws SQLException {
        Long countryId = isoToCountryId.get(expected.isoCode());
        assertThat(countryId)
                .as("core country id for %s", expected.isoCode())
                .isNotNull();

        try (Connection conn = DriverManager.getConnection(
                urls.locJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT cat.is_mandatory, cat.sequence, cat.validation_regex
                     FROM localization.country_address_template cat
                     JOIN localization.address_template_field f ON f.id = cat.address_template_field_id
                     WHERE cat.country_id = ? AND f.field_key = ?
                     """)) {
            ps.setLong(1, countryId);
            ps.setString(2, expected.fieldKey());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("row %s/%s", expected.isoCode(), expected.fieldKey())
                        .isTrue();
                assertThat(rs.getBoolean("is_mandatory")).isEqualTo(expected.mandatory());
                assertThat(rs.getInt("sequence")).isEqualTo(expected.sequence());
                String regex = rs.getString("validation_regex");
                if (expected.validationRegex() == null) {
                    assertThat(regex).isNull();
                } else {
                    assertThat(regex).isEqualTo(expected.validationRegex());
                }
            }
        }
    }
}

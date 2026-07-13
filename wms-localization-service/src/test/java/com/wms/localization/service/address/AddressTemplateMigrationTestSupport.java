package com.wms.localization.service.address;

import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Core + localization Flyway migration zincirini Testcontainers üzerinde kurar.
 */
final class AddressTemplateMigrationTestSupport {

    static final String CORE_DB = "wms_core_db";
    static final String LOC_DB = "wms_localization_db";

    private AddressTemplateMigrationTestSupport() {}

    record MigrationUrls(String coreJdbcUrl, String locJdbcUrl) {}

    static MigrationUrls setupAndMigrate(PostgreSQLContainer<?> postgres) throws SQLException {
        String baseUrl = postgres.getJdbcUrl();
        String username = postgres.getUsername();
        String password = postgres.getPassword();

        try (Connection admin = DriverManager.getConnection(baseUrl, username, password)) {
            admin.createStatement().execute("CREATE DATABASE " + CORE_DB);
            admin.createStatement().execute("CREATE DATABASE " + LOC_DB);
        }

        String coreJdbcUrl = baseUrl.replace("/" + postgres.getDatabaseName(), "/" + CORE_DB);
        String locJdbcUrl = baseUrl.replace("/" + postgres.getDatabaseName(), "/" + LOC_DB);

        Path coreMigrations = Path.of(System.getProperty("user.dir"))
                .getParent()
                .resolve("wms-core-service/src/main/resources/db/migration");

        Flyway.configure()
                .dataSource(coreJdbcUrl, username, password)
                .locations("filesystem:" + coreMigrations.toAbsolutePath())
                .load()
                .migrate();

        Flyway.configure()
                .dataSource(locJdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        return new MigrationUrls(coreJdbcUrl, locJdbcUrl);
    }
}

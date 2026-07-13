package com.wms.inventory;

import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Flyway migration zincirini gerçek PostgreSQL (Testcontainers) üzerinde kurar.
 * dblink kullanan migration'lar upstream veritabanlarının aynı instance üzerinde
 * postgres/postgres kimlik bilgileriyle mevcut olmasını gerektirir; bu yüzden
 * zincir migration bağımlılık sırasıyla verilmelidir.
 */
final class MigrationSmokeSupport {

    private MigrationSmokeSupport() {
    }

    /** Monorepo'daki kardeş modülün migration klasörünü filesystem location olarak döner. */
    static String moduleMigrations(String moduleName) {
        return "filesystem:" + Path.of(System.getProperty("user.dir"))
                .getParent()
                .resolve(moduleName)
                .resolve("src/main/resources/db/migration")
                .toAbsolutePath();
    }

    /** Verilen sırayla veritabanlarını oluşturur ve migrate eder; son DB'nin JDBC URL'ini döner. */
    static String migrateChain(PostgreSQLContainer<?> postgres, LinkedHashMap<String, String> databases)
            throws SQLException {
        String baseUrl = postgres.getJdbcUrl();
        try (Connection admin = DriverManager.getConnection(baseUrl, postgres.getUsername(), postgres.getPassword())) {
            for (String dbName : databases.keySet()) {
                admin.createStatement().execute("CREATE DATABASE " + dbName);
            }
        }
        String lastUrl = null;
        for (Map.Entry<String, String> entry : databases.entrySet()) {
            lastUrl = baseUrl.replace("/" + postgres.getDatabaseName(), "/" + entry.getKey());
            Flyway.configure()
                    .dataSource(lastUrl, postgres.getUsername(), postgres.getPassword())
                    .locations(entry.getValue())
                    .load()
                    .migrate();
        }
        return lastUrl;
    }
}

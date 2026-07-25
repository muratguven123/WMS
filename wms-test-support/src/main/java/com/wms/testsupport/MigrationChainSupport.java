package com.wms.testsupport;

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
 *
 * <p>dblink kullanan migration'lar upstream veritabanlarının aynı instance üzerinde
 * postgres/postgres kimlik bilgileriyle mevcut olmasını gerektirir; bu yüzden zincir
 * migration bağımlılık sırasıyla verilmelidir (ör. billing için core &rarr; finance &rarr; billing).
 *
 * <p>Daha önce billing/finance/integration/inventory/outbound modüllerinde birebir
 * kopyalanan {@code MigrationSmokeSupport} sınıfının tek kaynağıdır. Servis testleri
 * bu modülü {@code test} scope bağımlılığı olarak ekleyip buradaki statik metotları çağırır.
 */
public final class MigrationChainSupport {

    private MigrationChainSupport() {
    }

    /**
     * Monorepo'daki kardeş modülün migration klasörünü Flyway filesystem location olarak döner.
     *
     * <p>{@code user.dir} testi koşturan servis modülünün dizinidir (Maven testi modül
     * basedir'inde çalıştırır); bu yüzden parent = monorepo kökü, resolve(moduleName) = kardeş modül.
     */
    public static String moduleMigrations(String moduleName) {
        return "filesystem:" + Path.of(System.getProperty("user.dir"))
                .getParent()
                .resolve(moduleName)
                .resolve("src/main/resources/db/migration")
                .toAbsolutePath();
    }

    /**
     * Verilen sırayla veritabanlarını oluşturur ve migrate eder; son DB'nin JDBC URL'ini döner.
     *
     * @param postgres  çalışan Testcontainers PostgreSQL instance'ı
     * @param databases sıralı map: db adı &rarr; Flyway location (classpath: veya filesystem:)
     */
    public static String migrateChain(PostgreSQLContainer<?> postgres, LinkedHashMap<String, String> databases)
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

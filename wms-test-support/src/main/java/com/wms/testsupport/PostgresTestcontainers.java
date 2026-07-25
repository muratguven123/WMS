package com.wms.testsupport;

import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.LinkedHashMap;

/**
 * Testcontainers PostgreSQL + Flyway migration zinciri başlatıcı.
 * Docker yoksa {@code null} döner; çağıran sınıf testleri {@code @EnabledIf} ile atlar.
 */
public final class PostgresTestcontainers {

    public record Started(PostgreSQLContainer<?> container, String jdbcUrl) {
    }

    private PostgresTestcontainers() {
    }

    public static Started start(LinkedHashMap<String, String> migrationChain) {
        if (!DockerSupport.isAvailable()) {
            return null;
        }
        @SuppressWarnings("resource")
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withUsername("postgres")
                .withPassword("postgres");
        postgres.start();
        try {
            String jdbcUrl = MigrationChainSupport.migrateChain(postgres, migrationChain);
            return new Started(postgres, jdbcUrl);
        } catch (SQLException e) {
            throw new IllegalStateException("Migration zinciri kurulamadı", e);
        }
    }
}

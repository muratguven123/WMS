package com.wms.inbound;

import com.wms.testsupport.MigrationChainSupport;
import com.wms.testsupport.PostgresTestcontainers;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.LinkedHashMap;

/**
 * Inbound entegrasyon/repository testleri için ortak taban.
 *
 * <p>Gerçek PostgreSQL 16 (Testcontainers) üzerinde dblink zinciri (core &rarr; inbound)
 * bir kez migrate edilir; JPA bu migrate edilmiş {@code wms_inbound_db}'ye bağlanır.
 * Inbound {@code V4__uuid_to_bigint.sql}, core'un BIGINT geçişini önce gerektirdiği için
 * zincir core &rarr; inbound sırasıyla kurulur. Flyway test tarafında kapatılır (şema zaten
 * kurulu), Hibernate şemaya dokunmaz ({@code ddl-auto=none}). Böylece test, üretim şemasının
 * birebir aynısında çalışır — H2'nin taklit edemediği JSONB/TIMESTAMPTZ/partial index dahil.
 *
 * <p>Container ve migration statik blokta bir kez kurulur; bu yüzden {@code @DynamicPropertySource}
 * çözümlenirken JDBC URL hazırdır. Testler bu sınıfı extend eder ve kendi test-tipi
 * anotasyonunu (ör. {@code @DataJpaTest}) taşır.
 *
 * <p>Docker yoksa testler atlanır ({@code @EnabledIf}).
 */
@EnabledIf("com.wms.testsupport.DockerSupport#isAvailable")
public abstract class InboundPostgresTestBase {

    static final PostgreSQLContainer<?> POSTGRES;
    static final String JDBC_URL;

    static {
        PostgresTestcontainers.Started started = PostgresTestcontainers.start(migrationChain());
        POSTGRES = started != null ? started.container() : null;
        JDBC_URL = started != null ? started.jdbcUrl() : null;
    }

    private static LinkedHashMap<String, String> migrationChain() {
        LinkedHashMap<String, String> chain = new LinkedHashMap<>();
        chain.put("wms_core_db", MigrationChainSupport.moduleMigrations("wms-core-service"));
        chain.put("wms_inbound_db", "classpath:db/migration");
        return chain;
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        if (JDBC_URL == null) {
            return;
        }
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }
}

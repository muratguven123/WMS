package com.wms.integration.repository;

import com.wms.integration.IntegrationPostgresTestBase;
import com.wms.integration.entity.IntegrationJob;
import com.wms.integration.entity.IntegrationSystem;
import com.wms.integration.entity.LocationIntegrationConfig;
import com.wms.integration.entity.enums.ConnectionType;
import com.wms.integration.entity.enums.IntegrationDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IntegrationSystem / IntegrationJob / LocationIntegrationConfig repository'lerinin
 * özel sorguları için gerçek-PostgreSQL entegrasyon testi.
 *
 * <p>Özellikle {@link LocationIntegrationConfigRepository#findActiveByLocationId} —
 * {@code ErpAdapterFactory}'nin birincil sorgusu — JOIN FETCH ve çift aktiflik koşulu
 * (config.isActive AND system.isActive) üzerinden doğrulanır. Migration seed'i commit'li
 * olduğundan testler benzersiz kod/locationId kullanır ve "contains" mantığıyla yazılır.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Integration config/system/job repository (gerçek PostgreSQL)")
class IntegrationConfigRepositoryTest extends IntegrationPostgresTestBase {

    @Autowired
    private IntegrationSystemRepository systemRepository;
    @Autowired
    private IntegrationJobRepository jobRepository;
    @Autowired
    private LocationIntegrationConfigRepository configRepository;

    private IntegrationSystem persistSystem(String code, boolean active) {
        IntegrationSystem system = IntegrationSystem.builder()
                .code(code)
                .name(code + " ERP")
                .build();
        system.setActive(active);
        return systemRepository.saveAndFlush(system);
    }

    private LocationIntegrationConfig persistConfig(Long locationId, IntegrationSystem system, boolean active) {
        LocationIntegrationConfig config = LocationIntegrationConfig.builder()
                .locationId(locationId)
                .integrationSystem(system)
                .connectionType(ConnectionType.REST)
                .build();
        config.setActive(active);
        return configRepository.saveAndFlush(config);
    }

    private IntegrationJob persistJob(String code, IntegrationDirection direction, boolean active) {
        IntegrationJob job = IntegrationJob.builder()
                .code(code)
                .name(code + " Job")
                .direction(direction)
                .build();
        job.setActive(active);
        return jobRepository.saveAndFlush(job);
    }

    // ---------- IntegrationSystem ----------

    @Test
    @DisplayName("existsByCode — var/yok doğru döner")
    void existsByCode() {
        persistSystem("TESTSYS_EX", true);
        assertThat(systemRepository.existsByCode("TESTSYS_EX")).isTrue();
        assertThat(systemRepository.existsByCode("NO_SUCH_CODE_XYZ")).isFalse();
    }

    @Test
    @DisplayName("findByCodeAndIsActiveTrue — pasif sistem dönmez")
    void findActiveSystem() {
        persistSystem("TESTSYS_ACTIVE", true);
        persistSystem("TESTSYS_INACTIVE", false);

        assertThat(systemRepository.findByCodeAndIsActiveTrue("TESTSYS_ACTIVE")).isPresent();
        assertThat(systemRepository.findByCodeAndIsActiveTrue("TESTSYS_INACTIVE")).isEmpty();
    }

    // ---------- IntegrationJob ----------

    @Test
    @DisplayName("IntegrationJob findByCodeAndIsActiveTrue + yön filtresi")
    void jobQueries() {
        persistJob("TESTJOB_IN", IntegrationDirection.INBOUND, true);
        persistJob("TESTJOB_OUT_INACTIVE", IntegrationDirection.OUTBOUND, false);

        assertThat(jobRepository.findByCodeAndIsActiveTrue("TESTJOB_IN")).isPresent();
        assertThat(jobRepository.findByCodeAndIsActiveTrue("TESTJOB_OUT_INACTIVE")).isEmpty();

        List<IntegrationJob> inbound = jobRepository.findByDirectionAndIsActiveTrue(IntegrationDirection.INBOUND);
        assertThat(inbound).extracting(IntegrationJob::getCode).contains("TESTJOB_IN");
        assertThat(inbound).allMatch(IntegrationJob::isActive);
    }

    // ---------- LocationIntegrationConfig ----------

    @Test
    @DisplayName("findActiveByLocationId — config ve sistem aktifse JOIN FETCH ile döner")
    void findActiveByLocationId_bothActive() {
        IntegrationSystem system = persistSystem("TESTSYS_CFG1", true);
        persistConfig(900001L, system, true);

        Optional<LocationIntegrationConfig> found = configRepository.findActiveByLocationId(900001L);

        assertThat(found).isPresent();
        // JOIN FETCH — sistem eager yüklenmeli.
        assertThat(found.get().getIntegrationSystem().getCode()).isEqualTo("TESTSYS_CFG1");
    }

    @Test
    @DisplayName("findActiveByLocationId — sistem pasifse boş döner")
    void findActiveByLocationId_systemInactive() {
        IntegrationSystem system = persistSystem("TESTSYS_CFG2", false);
        persistConfig(900002L, system, true);

        assertThat(configRepository.findActiveByLocationId(900002L)).isEmpty();
    }

    @Test
    @DisplayName("findActiveByLocationId — config pasifse boş döner")
    void findActiveByLocationId_configInactive() {
        IntegrationSystem system = persistSystem("TESTSYS_CFG3", true);
        persistConfig(900003L, system, false);

        assertThat(configRepository.findActiveByLocationId(900003L)).isEmpty();
    }

    @Test
    @DisplayName("findByIntegrationSystem_CodeAndIsActiveTrue — sisteme bağlı aktif config'ler")
    void findBySystemCode() {
        IntegrationSystem system = persistSystem("TESTSYS_CFG4", true);
        persistConfig(900004L, system, true);
        persistConfig(900005L, system, false);

        List<LocationIntegrationConfig> active =
                configRepository.findByIntegrationSystem_CodeAndIsActiveTrue("TESTSYS_CFG4");

        assertThat(active).extracting(LocationIntegrationConfig::getLocationId).contains(900004L);
        assertThat(active).extracting(LocationIntegrationConfig::getLocationId).doesNotContain(900005L);
    }

    @Test
    @DisplayName("findAllActiveWithSystem — yalnız çift-aktif config'ler")
    void findAllActiveWithSystem() {
        IntegrationSystem active = persistSystem("TESTSYS_CFG5", true);
        IntegrationSystem inactive = persistSystem("TESTSYS_CFG6", false);
        persistConfig(900006L, active, true);
        persistConfig(900007L, inactive, true);

        List<LocationIntegrationConfig> result = configRepository.findAllActiveWithSystem();

        assertThat(result).extracting(LocationIntegrationConfig::getLocationId).contains(900006L);
        assertThat(result).extracting(LocationIntegrationConfig::getLocationId).doesNotContain(900007L);
    }
}

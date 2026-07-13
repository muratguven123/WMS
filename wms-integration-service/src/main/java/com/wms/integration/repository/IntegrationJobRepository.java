package com.wms.integration.repository;

import com.wms.integration.entity.IntegrationJob;
import com.wms.integration.entity.enums.IntegrationDirection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationJobRepository extends JpaRepository<IntegrationJob, Long> {

    Optional<IntegrationJob> findByCodeAndIsActiveTrue(String code);

    List<IntegrationJob> findByDirectionAndIsActiveTrue(IntegrationDirection direction);
}

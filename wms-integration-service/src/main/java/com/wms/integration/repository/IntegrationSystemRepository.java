package com.wms.integration.repository;

import com.wms.integration.entity.IntegrationSystem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntegrationSystemRepository extends JpaRepository<IntegrationSystem, Long> {

    Optional<IntegrationSystem> findByCodeAndIsActiveTrue(String code);

    boolean existsByCode(String code);
}

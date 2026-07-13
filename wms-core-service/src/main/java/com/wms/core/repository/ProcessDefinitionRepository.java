package com.wms.core.repository;

import com.wms.core.entity.ProcessDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessDefinitionRepository extends JpaRepository<ProcessDefinition, Long> {

    Optional<ProcessDefinition> findByCode(String code);

    boolean existsByCode(String code);
}

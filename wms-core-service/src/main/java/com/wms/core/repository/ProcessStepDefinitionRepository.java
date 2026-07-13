package com.wms.core.repository;

import com.wms.core.entity.ProcessStepDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessStepDefinitionRepository extends JpaRepository<ProcessStepDefinition, Long> {

    Optional<ProcessStepDefinition> findByCode(String code);

    /**
     * Belirli bir süreç tanımına ait tüm adımları varsayılan sırasıyla getirir.
     */
    List<ProcessStepDefinition> findByProcessDefinitionIdOrderByDefaultSequenceAsc(Long processDefinitionId);
}

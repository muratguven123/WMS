package com.wms.finance.repository;

import com.wms.finance.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    @Query("""
            SELECT sc FROM SystemConfig sc
            JOIN FETCH sc.defaultCurrency
            """)
    Optional<SystemConfig> findFirstWithDefaultCurrency();
}

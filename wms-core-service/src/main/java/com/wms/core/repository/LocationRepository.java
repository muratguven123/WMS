package com.wms.core.repository;

import com.wms.core.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByCompanyId(Long companyId);

    List<Location> findByRegionId(Long regionId);

    List<Location> findByCompanyIdAndIsActiveTrue(Long companyId);

    Optional<Location> findByCompanyIdAndId(Long companyId, Long id);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIsActiveTrue(Long companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIsActiveTrueAndIdNot(
            Long companyId, String name, Long excludeId);
}

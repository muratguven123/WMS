package com.wms.core.repository;

import com.wms.core.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {

    List<Region> findByCountryId(Long countryId);
}

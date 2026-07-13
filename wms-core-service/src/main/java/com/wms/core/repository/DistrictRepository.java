package com.wms.core.repository;

import com.wms.core.entity.District;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DistrictRepository extends JpaRepository<District, Long> {

    /** Cascade dropdown: şehire bağlı ilçeler. */
    List<District> findByCityIdOrderByNameAsc(Long cityId);

    /** Cross-validation (İş Kuralı 2): district'in gerçekten o city'e ait olduğunu doğrula. */
    boolean existsByIdAndCityId(Long districtId, Long cityId);

    boolean existsByCityIdAndName(Long cityId, String name);
}

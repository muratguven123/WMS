package com.wms.core.repository;

import com.wms.core.entity.Neighborhood;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NeighborhoodRepository extends JpaRepository<Neighborhood, Long> {

    /** Cascade dropdown: ilçeye bağlı mahalleler (zipCode dahil — otomatik doldurma için). */
    List<Neighborhood> findByDistrictIdOrderByNameAsc(Long districtId);

    /** Posta kodu ile arama — adres import/eşleştirme senaryoları için. */
    Optional<Neighborhood> findByZipCode(String zipCode);

    boolean existsByDistrictIdAndName(Long districtId, String name);
}

package com.wms.finance.repository;

import com.wms.finance.entity.TaxType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxTypeRepository extends JpaRepository<TaxType, Long> {

    Optional<TaxType> findByCode(String code);

    List<TaxType> findAllByActiveTrue();

    boolean existsByCode(String code);
}

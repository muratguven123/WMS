package com.wms.core.repository;

import com.wms.core.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findByOrganizationId(Long organizationId);

    Optional<Company> findByTaxNumber(String taxNumber);

    boolean existsByTaxNumber(String taxNumber);
}

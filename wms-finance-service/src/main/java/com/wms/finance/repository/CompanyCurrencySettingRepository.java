package com.wms.finance.repository;

import com.wms.finance.entity.CompanyCurrencySetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyCurrencySettingRepository extends JpaRepository<CompanyCurrencySetting, Long> {

    @Query("""
            SELECT ccs FROM CompanyCurrencySetting ccs
            JOIN FETCH ccs.baseCurrency
            WHERE ccs.companyId = :companyId
            """)
    Optional<CompanyCurrencySetting> findByCompanyIdWithCurrency(Long companyId);
}

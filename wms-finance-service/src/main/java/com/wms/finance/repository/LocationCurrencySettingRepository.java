package com.wms.finance.repository;

import com.wms.finance.entity.LocationCurrencySetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocationCurrencySettingRepository extends JpaRepository<LocationCurrencySetting, Long> {

    @Query("""
            SELECT lcs FROM LocationCurrencySetting lcs
            JOIN FETCH lcs.localCurrency
            WHERE lcs.locationId = :locationId
            """)
    Optional<LocationCurrencySetting> findByLocationIdWithCurrency(Long locationId);
}

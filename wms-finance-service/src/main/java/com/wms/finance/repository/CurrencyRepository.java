package com.wms.finance.repository;

import com.wms.finance.entity.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, Long> {

    Optional<Currency> findByCodeAndActiveTrue(String code);

    List<Currency> findAllByActiveTrue();
}

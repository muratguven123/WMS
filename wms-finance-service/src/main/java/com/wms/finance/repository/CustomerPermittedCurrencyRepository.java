package com.wms.finance.repository;

import com.wms.finance.entity.CustomerPermittedCurrency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerPermittedCurrencyRepository extends JpaRepository<CustomerPermittedCurrency, Long> {

    List<CustomerPermittedCurrency> findByCustomerId(Long customerId);

    boolean existsByCustomerIdAndCurrencyId(Long customerId, Long currencyId);

    @Query("""
            SELECT cpc FROM CustomerPermittedCurrency cpc
            JOIN FETCH cpc.currency
            WHERE cpc.customer.id = :customerId
            """)
    List<CustomerPermittedCurrency> findByCustomerIdWithCurrency(Long customerId);
}

package com.wms.billing.repository;

import com.wms.billing.domain.entity.Invoice;
import com.wms.billing.domain.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByCustomerId(Long customerId);

    List<Invoice> findByCustomerIdAndStatus(Long customerId, InvoiceStatus status);

    List<Invoice> findByLocationId(Long locationId);

    List<Invoice> findByStatus(InvoiceStatus status);

    List<Invoice> findByIssueDateBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Müşteriye ait faturaları, kalemleriyle birlikte tek sorguda getirir (N+1 önlemi).
     */
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items WHERE i.customerId = :customerId")
    List<Invoice> findByCustomerIdWithItems(@Param("customerId") Long customerId);

    /**
     * Belirli para birimi kombinasyonuna sahip faturaları listeler.
     */
    List<Invoice> findByInvoiceCurrencyAndAccountingCurrency(
            String invoiceCurrency, String accountingCurrency);

    Page<Invoice> findByLocationId(Long locationId, Pageable pageable);

    Page<Invoice> findByLocationIdAndCustomerId(Long locationId, Long customerId, Pageable pageable);

    Page<Invoice> findByLocationIdAndStatus(Long locationId, InvoiceStatus status, Pageable pageable);

    Page<Invoice> findByLocationIdAndCustomerIdAndStatus(
            Long locationId, Long customerId, InvoiceStatus status, Pageable pageable);

    long countByLocationIdAndIssueDateBetween(
            Long locationId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items WHERE i.id = :id")
    Optional<Invoice> findByIdWithItems(@Param("id") Long id);
}

package com.wms.billing.repository;

import com.wms.billing.BillingPostgresTestBase;
import com.wms.billing.domain.entity.Invoice;
import com.wms.billing.domain.enums.InvoiceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InvoiceRepository} gerçek-PostgreSQL entegrasyon testi.
 *
 * <p>Test kendi verisini kurar (paylaşımlı seed'e bağımlı değildir). BigDecimal
 * precision/scale ve enum kolonları üretim şemasına karşı doğrulanır.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("InvoiceRepository (gerçek PostgreSQL)")
class InvoiceRepositoryTest extends BillingPostgresTestBase {

    @Autowired
    private InvoiceRepository repository;

    private static Invoice.InvoiceBuilder base(String number, Long customerId, InvoiceStatus status) {
        BigDecimal amount = new BigDecimal("100.0000");
        return Invoice.builder()
                .invoiceNumber(number)
                .customerId(customerId)
                .locationId(1L)
                .issueDate(LocalDateTime.now())
                .invoiceCurrency("USD")
                .accountingCurrency("TRY")
                .exchangeRateDate(LocalDate.now())
                .exchangeRateValue(new BigDecimal("34.500000"))
                .subtotalOriginal(amount)
                .taxAmountOriginal(new BigDecimal("20.0000"))
                .grandTotalOriginal(new BigDecimal("120.0000"))
                .grandTotalAccounting(new BigDecimal("4140.0000"))
                .status(status);
    }

    @Test
    @DisplayName("findByInvoiceNumber / existsByInvoiceNumber kaydı bulur")
    void findByInvoiceNumber() {
        repository.save(base("INV-1001", 50L, InvoiceStatus.APPROVED).build());

        assertThat(repository.findByInvoiceNumber("INV-1001")).isPresent();
        assertThat(repository.existsByInvoiceNumber("INV-1001")).isTrue();
        assertThat(repository.existsByInvoiceNumber("INV-YOK")).isFalse();
    }

    @Test
    @DisplayName("findByCustomerIdAndStatus yalnız eşleşen müşteri+durum kayıtlarını döner")
    void findByCustomerIdAndStatus() {
        repository.save(base("INV-2001", 60L, InvoiceStatus.DRAFT).build());
        repository.save(base("INV-2002", 60L, InvoiceStatus.APPROVED).build());
        repository.save(base("INV-2003", 61L, InvoiceStatus.DRAFT).build());

        List<Invoice> result = repository.findByCustomerIdAndStatus(60L, InvoiceStatus.DRAFT);

        assertThat(result).extracting(Invoice::getInvoiceNumber).containsExactly("INV-2001");
    }

    @Test
    @DisplayName("findByInvoiceCurrencyAndAccountingCurrency para birimi kombinasyonunu filtreler")
    void findByCurrencyPair() {
        repository.save(base("INV-3001", 70L, InvoiceStatus.APPROVED).build());
        repository.save(base("INV-3002", 70L, InvoiceStatus.APPROVED)
                .invoiceCurrency("EUR").build());

        List<Invoice> usdTry = repository.findByInvoiceCurrencyAndAccountingCurrency("USD", "TRY");

        assertThat(usdTry).extracting(Invoice::getInvoiceNumber).containsExactly("INV-3001");
    }
}

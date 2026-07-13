package com.wms.billing.domain.entity;

import com.wms.billing.domain.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fatura başlık (header) tablosu.
 *
 * <p>Kur değeri locking: Faturalama anındaki kur {@code exchangeRateValue} alanına
 * kopyalanır; döviz tanım tablosuna runtime FK bağlantısı kurulmaz.
 * Bu sayede kur ilerleyen günlerde değişse bile fatura tutarları sabit kalır.</p>
 */
@Entity
@Table(name = "invoices",
        uniqueConstraints = @UniqueConstraint(name = "uk_invoices_number", columnNames = "invoice_number"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "invoice_number", nullable = false, length = 64)
    private String invoiceNumber;

    /** Müşteri kimliği — wms-core-service'ten referans (cross-service FK, sadece Long saklanır). */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** Lokasyon kimliği — wms-core-service'ten referans. */
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "issue_date", nullable = false)
    private LocalDateTime issueDate;

    /**
     * Fatura para birimi kodu (ISO 4217), örn. "EUR", "USD".
     * Döviz tanım tablosuna FK yerine sadece kod saklıyoruz; tanımlar değişse bile
     * fatura tarihsel bütünlüğünü korur.
     */
    @Column(name = "invoice_currency", nullable = false, length = 3)
    private String invoiceCurrency;

    /** Muhasebe/yerel para birimi kodu, örn. "TRY", "EUR". */
    @Column(name = "accounting_currency", nullable = false, length = 3)
    private String accountingCurrency;

    /** Kurun hangi tarihe ait olduğu. */
    @Column(name = "exchange_rate_date", nullable = false)
    private LocalDate exchangeRateDate;

    /**
     * Faturalama anında dondurulmuş kur değeri (invoiceCurrency → accountingCurrency).
     * precision=18, scale=6 — kur hassasiyeti için.
     */
    @Column(name = "exchange_rate_value", nullable = false, precision = 18, scale = 6)
    private BigDecimal exchangeRateValue;

    /** İşlem para birimi cinsinden KDV hariç ara toplam. */
    @Column(name = "subtotal_original", nullable = false, precision = 18, scale = 4)
    private BigDecimal subtotalOriginal;

    /** İşlem para birimi cinsinden toplam vergi tutarı. */
    @Column(name = "tax_amount_original", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmountOriginal;

    /** İşlem para birimi cinsinden genel toplam (subtotal + tax). */
    @Column(name = "grand_total_original", nullable = false, precision = 18, scale = 4)
    private BigDecimal grandTotalOriginal;

    /** Muhasebe para birimi cinsinden genel toplam (grandTotalOriginal × exchangeRateValue). */
    @Column(name = "grand_total_accounting", nullable = false, precision = 18, scale = 4)
    private BigDecimal grandTotalAccounting;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "invoice",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "invoice",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<ExchangeDifferenceLog> exchangeDifferenceLogs = new ArrayList<>();

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = InvoiceStatus.DRAFT;
        }
    }
}

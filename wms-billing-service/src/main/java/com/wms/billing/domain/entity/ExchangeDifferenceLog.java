package com.wms.billing.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Kur farkı kayıt tablosu.
 *
 * <p>Fatura oluşturulurken dondurulmuş kur ile ödeme anındaki gerçek kur arasındaki
 * farkı ve bu farka karşı alınan aksiyonu kaydeder.</p>
 */
@Entity
@Table(name = "exchange_difference_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeDifferenceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_exch_diff_logs_invoice"))
    private Invoice invoice;

    @Column(name = "calculation_date", nullable = false)
    private LocalDateTime calculationDate;

    /** Ödeme anında fatura para birimindeki ödenen tutar. */
    @Column(name = "original_paid_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal originalPaidAmount;

    /** Ödeme anındaki gerçek kur (invoiceCurrency → accountingCurrency). precision=18, scale=6. */
    @Column(name = "rate_at_payment", nullable = false, precision = 18, scale = 6)
    private BigDecimal rateAtPayment;

    /**
     * Kur farkı tutarı (muhasebe para birimi).
     * Pozitif = kur kazancı, Negatif = kur kaybı.
     */
    @Column(name = "exchange_difference_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal exchangeDifferenceAmount;

    /** Alınan aksiyon açıklaması, örn. "POSTED_TO_ERP", "MANUAL_ADJUSTMENT". */
    @Column(name = "action_taken", nullable = false, length = 100)
    private String actionTaken;

    /** Pozitif kur farkı üzerinden finance motorundan hesaplanan vergi tutarı. */
    @Column(name = "tax_amount", precision = 18, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "tax_type_code", length = 32)
    private String taxTypeCode;
}

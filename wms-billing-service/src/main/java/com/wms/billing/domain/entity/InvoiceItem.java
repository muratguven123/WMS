package com.wms.billing.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Fatura satır (line item) tablosu.
 *
 * <p>Tüm tutar alanları işlem (fatura) para birimi cinsindendir.
 * Muhasebe para birimine dönüşüm {@link Invoice#exchangeRateValue} üzerinden yapılır.</p>
 */
@Entity
@Table(name = "invoice_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_invoice_items_invoice"))
    private Invoice invoice;

    @Column(name = "item_description", nullable = false, length = 500)
    private String itemDescription;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    /** Birim fiyat (işlem para birimi). */
    @Column(name = "unit_price_original", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPriceOriginal;

    /** Satır bazında iskonto tutarı (işlem para birimi). */
    @Column(name = "discount_original", nullable = false, precision = 18, scale = 4)
    private BigDecimal discountOriginal;

    /** KDV/vergi oranı yüzde olarak, örn. 20.00 → %20. precision=5, scale=2. */
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    /** Hesaplanan vergi tutarı (işlem para birimi). */
    @Column(name = "tax_amount_original", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmountOriginal;

    /**
     * Satır genel toplamı — KDV hariç matrah (işlem para birimi).
     * Formül: (quantity × unitPriceOriginal) − discountOriginal
     */
    @Column(name = "line_total_original", nullable = false, precision = 18, scale = 4)
    private BigDecimal lineTotalOriginal;
}

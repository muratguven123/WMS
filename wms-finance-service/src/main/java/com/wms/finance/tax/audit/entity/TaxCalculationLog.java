package com.wms.finance.tax.audit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Her vergi hesaplama işleminin denetime uygun (audit-ready) kaydını tutar.
 *
 * <p><b>Tasarım Kararları:</b>
 * <ul>
 *   <li>{@code @Immutable} — audit log kayıtları hiçbir zaman güncellenmez;
 *       hata durumunda yeni bir ters kayıt (reversal) oluşturulur.</li>
 *   <li>{@code taxRate} log'a kopyalanır — TaxType'ın default_rate'i ileride
 *       değişse bile hesaplama anındaki oran korunur.</li>
 *   <li>{@code transactionReferenceId} generic Long'dir; farklı modüllerdeki
 *       (fatura, ücret vb.) kayıtlara FK olmadan bağlantı kurulur.</li>
 * </ul>
 *
 * <p><b>İndeksler:</b>
 * <ul>
 *   <li>{@code idx_tax_log_tx_type_ref} — (transaction_type, transaction_reference_id)
 *       bileşik indeksi; raporlama sorgularını optimize eder.</li>
 *   <li>{@code idx_tax_log_calculation_date} — tarih bazlı dönem sorgularını hızlandırır.</li>
 *   <li>{@code idx_tax_log_is_exempt} — muafiyet raporlamasını hızlandırır.</li>
 * </ul>
 */
@Entity
@Immutable
@Table(
        name = "tax_calculation_log",
        schema = "finance",
        indexes = {
                @Index(
                        name = "idx_tax_log_tx_type_ref",
                        columnList = "transaction_type, transaction_reference_id"
                ),
                @Index(
                        name = "idx_tax_log_calculation_date",
                        columnList = "calculation_date"
                ),
                @Index(
                        name = "idx_tax_log_is_exempt",
                        columnList = "is_exempt"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxCalculationLog {

    // -----------------------------------------------------------------------
    // Kimlik
    // -----------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    // -----------------------------------------------------------------------
    // İşlem Referansı
    // -----------------------------------------------------------------------

    /**
     * Hesaplamanın hangi işlem tipine ait olduğu.
     * DB'de VARCHAR olarak saklanır (enum ismi), yeni tipler migration gerektirmez.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 50)
    private TransactionType transactionType;

    /**
     * İlgili iş nesnesinin birincil anahtarı (INVOICE_LINE.id, TRANSACTION_FEE.id vb.).
     * Kasıtlı olarak DB-level FK tanımlanmamıştır; cross-module bağımsızlığını korur.
     */
    @Column(name = "transaction_reference_id", nullable = false, updatable = false)
    private Long transactionReferenceId;

    // -----------------------------------------------------------------------
    // Vergi Tipi (FK)
    // -----------------------------------------------------------------------

    /**
     * Hesaplamada kullanılan vergi tipi.
     * LAZY fetch: log listesi çekilirken TaxType join'i tetiklenmez.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "tax_type_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_tax_log_tax_type")
    )
    private TaxType taxType;

    // -----------------------------------------------------------------------
    // Hesaplama Değerleri
    // -----------------------------------------------------------------------

    /**
     * Hesaplama anındaki vergi oranı (TaxType.defaultRate'den kopyalanır).
     * Precision(5,2): 0.00 – 999.99 arasında oran saklanabilir.
     */
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    /**
     * Vergi matrahı.
     * Precision(18,4): büyük fatura tutarlarında hassas saklama.
     */
    @Column(name = "tax_base_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxBaseAmount;

    /**
     * Hesaplanan vergi tutarı.
     * Precision(18,4): matrah ile aynı hassasiyet.
     */
    @Column(name = "calculated_tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal calculatedTaxAmount;

    // -----------------------------------------------------------------------
    // Hesaplama Meta Verileri
    // -----------------------------------------------------------------------

    /**
     * {@code true} → vergi brüt tutara dahildir (KDV dahil fiyatlandırma).
     * {@code false} → vergi matrah üzerine eklenir (KDV hariç fiyatlandırma).
     */
    @Column(name = "is_inclusive", nullable = false)
    private boolean inclusive;

    /**
     * {@code true} → bu işlem için vergi muafiyeti uygulandı.
     * Muafiyet varsa {@code calculatedTaxAmount} sıfır olmalıdır.
     */
    @Column(name = "is_exempt", nullable = false)
    private boolean exempt;

    /**
     * Yasal muafiyet kodu (örn: "IHRACAT_MUAF", "OIB_MUAF_2024").
     * Muafiyet yoksa ({@code exempt=false}) null bırakılır.
     */
    @Column(name = "exemption_code", length = 100)
    private String exemptionCode;

    /**
     * Hesaplamayı yapan motor/versiyon etiketi (örn: "TAX_ENGINE_V1", "MANUAL_ENTRY").
     * Olası hata ayıklamalarında hangi algoritmanın kullanıldığını izlemeye yarar.
     */
    @Column(name = "calculation_source", nullable = false, length = 100)
    private String calculationSource;

    /**
     * Vergi hesaplamasının yapıldığı zaman damgası.
     * {@code @CreationTimestamp} ile otomatik set edilir; güncellenmez.
     */
    @CreationTimestamp
    @Column(name = "calculation_date", nullable = false, updatable = false)
    private LocalDateTime calculationDate;

    // -----------------------------------------------------------------------
    // Yaşam Döngüsü Doğrulaması
    // -----------------------------------------------------------------------

    /**
     * Muafiyet tutarlılığını persist öncesinde doğrular:
     * <ul>
     *   <li>exempt=true  → exemptionCode dolu olmalı, calculatedTaxAmount sıfır olmalı</li>
     *   <li>exempt=false → exemptionCode null olmalı</li>
     * </ul>
     */
    @PrePersist
    private void validateExemption() {
        if (exempt) {
            if (exemptionCode == null || exemptionCode.isBlank()) {
                throw new IllegalStateException(
                        "Muafiyet uygulandığında exemptionCode zorunludur. transactionReferenceId=" + transactionReferenceId);
            }
            if (calculatedTaxAmount != null && calculatedTaxAmount.compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalStateException(
                        "Muafiyet uygulandığında calculatedTaxAmount sıfır olmalıdır. transactionReferenceId=" + transactionReferenceId);
            }
        } else {
            if (exemptionCode != null) {
                throw new IllegalStateException(
                        "Muafiyet yokken exemptionCode set edilemez. transactionReferenceId=" + transactionReferenceId);
            }
        }
    }
}

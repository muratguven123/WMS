package com.wms.finance.tax.audit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Sistemde tanımlı vergi tiplerini tutar (KDV, ÖTV, Stopaj vb.).
 *
 * <p>Her {@link TaxCalculationLog} kaydı bir TaxType'a FK ile bağlanır.
 * Bu tasarım, oran değişikliklerinde mevcut log kayıtlarının bozulmamasını
 * sağlar çünkü log, hesaplama anındaki oranı ayrıca saklar.
 */
@Entity(name = "AuditTaxType")
@Table(
        name = "tax_type",
        schema = "finance",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tax_type_audit_code", columnNames = "code")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * İnsan okunabilir benzersiz kod (örn: "KDV_20", "OTV_25", "STOPAJ_10").
     */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    /**
     * Kullanıcıya gösterilecek açıklama (örn: "Katma Değer Vergisi %20").
     */
    @Column(name = "description", nullable = false, length = 200)
    private String description;

    /**
     * Varsayılan vergi oranı. Hesaplama sırasında log'a kopyalanır
     * böylece gelecekte oran değişse de geçmiş kayıtlar etkilenmez.
     */
    @Column(name = "default_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultRate;

    /** Verginin aktif/pasif durumu. Pasif vergi tipleri seçilemez. */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

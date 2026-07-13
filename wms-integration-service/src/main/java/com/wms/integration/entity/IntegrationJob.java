package com.wms.integration.entity;

import com.wms.integration.entity.enums.IntegrationDirection;
import jakarta.persistence.*;
import lombok.*;

/**
 * Tanımlı entegrasyon iş tipleri.
 *
 * <p>Her iş tipi bir veri kategorisini (malzeme, stok hareketi, fatura vb.)
 * ve akış yönünü (INBOUND / OUTBOUND) temsil eder.
 *
 * <p>Örnek kayıtlar:
 * <pre>
 *   code=MAT_SYNC,    name="Material Card Sync",       direction=OUTBOUND
 *   code=STOCK_MOVE,  name="Inventory Movement Sync",  direction=OUTBOUND
 *   code=INVOICE_SYNC,name="Invoice Sync",             direction=OUTBOUND
 *   code=EX_RATE_PULL,name="Exchange Rate Pull",       direction=INBOUND
 * </pre>
 */
@Entity
@Table(name = "integration_jobs",
        indexes = {
                @Index(name = "idx_integration_job_code", columnList = "code")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationJob extends BaseEntity {

    /** Benzersiz iş kodu — Outbox Worker ve servis katmanında referans olarak kullanılır. */
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    /** Okunabilir iş adı. */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private IntegrationDirection direction;
}

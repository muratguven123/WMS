package com.wms.integration.entity;

import com.wms.integration.entity.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Transactional Outbox deseni için mesaj tablosu.
 *
 * <h3>Atomiklik Garantisi</h3>
 * İş işlemi (stok hareketi, fatura onayı vb.) ve bu kayıt,
 * {@link com.wms.integration.outbox.OutboxPublisherService} tarafından
 * <b>tek bir veritabanı transaction'ı</b> içinde yazılır. İş işlemi
 * rollback olursa Outbox kaydı da geri alınır; böylece mesaj kaybı yaşanmaz.
 *
 * <h3>Retry Stratejisi (Exponential Backoff)</h3>
 * <ul>
 *   <li>Her başarısız denemede {@code retryCount} artırılır.</li>
 *   <li>{@code nextAttemptAt = now + 2^retryCount dakika} olarak set edilir.</li>
 *   <li>{@code retryCount >= maxRetry} eşiğinde durum {@code FAILED_MAX_RETRIES}'e
 *       geçer ve admin bildirimi tetiklenir.</li>
 * </ul>
 *
 * <p>BaseEntity'deki {@code isActive} ve {@code updatedAt} Outbox context'inde
 * anlamsızdır; {@code @AttributeOverride} ile nullable yapılır.
 */
@Entity
@Table(name = "outbox_messages",
        indexes = {
                @Index(name = "idx_outbox_status_next_attempt",
                        columnList = "status, next_attempt_at"),
                @Index(name = "idx_outbox_aggregate",
                        columnList = "aggregate_type, aggregate_id"),
                @Index(name = "idx_outbox_created_at",
                        columnList = "created_at")
        })
@AttributeOverride(name = "isActive",  column = @Column(name = "is_active",  insertable = false, updatable = false, nullable = true))
@AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", insertable = false, updatable = false, nullable = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxMessage extends BaseEntity {

    /**
     * Aggregate tipi — hangi domain nesnesinin tetiklediğini gösterir.
     * Örn: "InventoryMovement", "Invoice", "MaterialCard"
     */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    /** Tetikleyici domain nesnesinin Long'si. */
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    /**
     * ERP job kodu — hangi entegrasyon işinin çalıştırılacağını belirtir.
     * {@link com.wms.integration.entity.IntegrationJob#getCode()} ile örtüşür.
     * Örn: "STOCK_MOVE", "INVOICE_SYNC", "MAT_SYNC"
     */
    @Column(name = "job_code", nullable = false, length = 50)
    private String jobCode;

    /**
     * ERP'ye gönderilecek JSON payload.
     * İlgili DTO ({@link com.wms.integration.adapter.dto.MovementDto} vb.)
     * ObjectMapper ile serileştirilip buraya yazılır.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * Kaynak lokasyon — {@link ErpAdapterFactory}'nin adaptör çözümlemesi için.
     * wms-core-service Location.id ile örtüşür; FK değil.
     */
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    /** Toplam başarısız deneme sayısı (ilk kayıt: 0). */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /**
     * Worker'ın bu kaydı işleyebileceği en erken zaman.
     * İlk kayıtta {@code NOW()} olarak set edilir.
     * Her hata sonrası {@code NOW() + 2^retryCount dakika} olarak güncellenir.
     */
    @Column(name = "next_attempt_at", nullable = false)
    @Builder.Default
    private OffsetDateTime nextAttemptAt = OffsetDateTime.now();

    /** Son deneme zamanı — izleme ve SLA hesabı için. */
    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    /** ERP'den dönen harici referans / belge numarası (başarı durumunda). */
    @Column(name = "external_reference", length = 200)
    private String externalReference;

    /** Son hata mesajı — debug ve admin ekranı için. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Kayıt oluşturma zamanı — BaseEntity.createdAt ile aynı rolde. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            insertable = false)   // BaseEntity zaten set ediyor
    private OffsetDateTime createdAt;
}

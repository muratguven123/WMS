package com.wms.integration.entity;

import com.wms.integration.entity.enums.IntegrationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * ERP entegrasyon denemelerinin detaylı kayıt defteri.
 *
 * <p>Her Outbox Worker gönderimi, başarısız retry veya başarılı teslim için
 * bir log satırı oluşturulur. Bu tablo hem operasyonel izleme (Force Retry API)
 * hem de post-mortem analiz için kullanılır.
 *
 * <h3>İndeksler</h3>
 * <ul>
 *   <li>{@code idx_int_log_status}       — RETRYING/FAILED kayıtlarını hızlı filtreler (Outbox Worker)</li>
 *   <li>{@code idx_int_log_created_at}   — Zaman aralığı sorguları ve 30 günlük temizleme job'ı</li>
 *   <li>{@code idx_int_log_config_id}    — Lokasyon bazlı log listeleme</li>
 *   <li>{@code idx_int_log_status_retry} — Bileşik: status + retryCount (Worker sıralama)</li>
 * </ul>
 *
 * <p>{@code BaseEntity}'deki {@code isActive} ve {@code updatedAt} alanları
 * bu entity'de anlamsız olduğundan {@code @AttributeOverride} ile nullable
 * yapılır; soft-delete yerine 30 günlük partition/temizleme kullanılır.
 */
@Entity
@Table(name = "integration_logs",
        indexes = {
                @Index(name = "idx_int_log_status",        columnList = "status"),
                @Index(name = "idx_int_log_created_at",    columnList = "created_at"),
                @Index(name = "idx_int_log_config_id",     columnList = "location_integration_config_id"),
                @Index(name = "idx_int_log_status_retry",  columnList = "status, retry_count")
        })
@AttributeOverride(name = "isActive",   column = @Column(name = "is_active", insertable = false, updatable = false, nullable = true))
@AttributeOverride(name = "updatedAt",  column = @Column(name = "updated_at", insertable = false, updatable = false, nullable = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_integration_config_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_int_log_config"))
    private LocationIntegrationConfig locationIntegrationConfig;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_job_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_int_log_job"))
    private IntegrationJob integrationJob;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private IntegrationStatus status;

    /**
     * ERP'ye gönderilen ham payload (JSON/XML/CSV).
     * Büyük içerikler için Text tipi uygundur; ileride BLOB'a migrate edilebilir.
     */
    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    /** ERP'den dönen ham yanıt. */
    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    /** Hata mesajı — SUCCESS durumunda null. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** ERP'nin döndüğü harici referans / belge numarası. */
    @Column(name = "external_reference", length = 200)
    private String externalReference;

    /** Toplam deneme sayısı (ilk deneme dahil). 0 = henüz denenmedi. */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /**
     * İlk kayıt zamanı — {@code BaseEntity.createdAt} zaten bu rolü üstlenir.
     * {@code lastAttemptAt} son deneme zamanını ayrıca tutar.
     */
    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    /**
     * Outbox message ID — İşlemi tetikleyen Outbox kaydıyla ilişki.
     * FK değil; servis bağımsızlığı korunur.
     */
    @Column(name = "outbox_message_id")
    private Long outboxMessageId;
}

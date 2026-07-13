package com.wms.core.entity;

import com.wms.core.entity.enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * {@code requiresApproval = true} olan bir süreç adımı tetiklendiğinde
 * oluşturulan onay talebi kaydı.
 *
 * <p>Aspect tarafından otomatik yaratılır; operasyon yöneticisi onaylayana
 * veya reddedene kadar işlem askıya alınır.</p>
 *
 * <p>Bu entity {@link BaseEntity}'yi extend <b>etmez</b>: onay kayıtları
 * soft-delete ile silinmemeli, tüm geçmiş korunmalıdır.</p>
 */
@Entity
@Table(
    name = "approval_requests",
    indexes = {
        @Index(name = "idx_approval_status",     columnList = "status"),
        @Index(name = "idx_approval_requested_by", columnList = "requested_by_user_id"),
        @Index(name = "idx_approval_step_config", columnList = "step_config_id"),
        @Index(name = "idx_approval_reference",  columnList = "reference_type, reference_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * Onay gerektiren süreç adımı konfigürasyonu.
     * Silme koruması için FK tanımlandı; adım config silinirse mevcut talepler korunur.
     */
    @Column(name = "step_config_id", nullable = false)
    private Long stepConfigId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "location_id")
    private Long locationId;

    /**
     * Onay gerektiren işlemin ait olduğu nesne tipi — örn: ORDER, TRANSFER, RECEIPT.
     */
    @Column(name = "reference_type", nullable = false, length = 50)
    private String referenceType;

    /**
     * Onay gerektiren işlemin nesne Long'si — örn: orderId, transferId.
     */
    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    /** İşlemi başlatan kullanıcı. */
    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    /** Onay veya reddetme işlemini yapan yönetici. Null ise henüz işleme alınmamış. */
    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING_APPROVAL;

    /** Onay/red açıklaması — yönetici tarafından doldurulur. */
    @Column(name = "reviewer_note", length = 500)
    private String reviewerNote;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

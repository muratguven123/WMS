package com.wms.core.service;

import com.wms.core.entity.ApprovalRequest;
import com.wms.core.entity.enums.ApprovalStatus;
import com.wms.core.exception.BusinessException;
import com.wms.core.messaging.ApprovalEventFactory;
import com.wms.core.repository.ApprovalRequestRepository;
import com.wms.core.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Onay talepleri yaşam döngüsünü yöneten servis.
 *
 * <p>Bir adım {@code requiresApproval = true} içeriyorsa
 * {@link WorkflowAspect} bu servisi çağırır; yönetici onaylayana/
 * reddedene kadar işlem askıda kalır.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalRequestService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalEventFactory approvalEventFactory;

    // -----------------------------------------------------------------------
    // Aspect tarafından çağrılır
    // -----------------------------------------------------------------------

    /**
     * Yeni bir {@link ApprovalStatus#PENDING_APPROVAL} talebi oluşturur.
     *
     * @param stepConfigId  Onay gerektiren adımın konfigürasyon ID'si
     * @param referenceType İşlemin nesne tipi — örn: RECEIPT, ORDER
     * @param referenceId   İşlemin nesne Long'si
     * @param requestedById İşlemi başlatan kullanıcı
     * @return Kaydedilmiş onay talebi
     */
    @Transactional
    public ApprovalRequest createPendingRequest(Long stepConfigId,
                                                String referenceType,
                                                Long referenceId,
                                                Long requestedById) {
        // Aynı referans için açık talep varsa tekrar oluşturma
        approvalRequestRepository
                .findByReferenceTypeAndReferenceIdAndStatus(
                        referenceType, referenceId, ApprovalStatus.PENDING_APPROVAL)
                .ifPresent(existing -> {
                    throw new BusinessException(
                            "Bu kayıt için zaten bekleyen bir onay talebi mevcut. approvalRequestId=" + existing.getId(),
                            HttpStatus.CONFLICT,
                            "APPROVAL_ALREADY_PENDING");
                });

        ApprovalRequest request = ApprovalRequest.builder()
                .stepConfigId(stepConfigId)
                .companyId(TenantContextHolder.getCompanyId())
                .locationId(TenantContextHolder.getLocationId())
                .referenceType(referenceType != null ? referenceType : "UNKNOWN")
                .referenceId(referenceId)
                .requestedByUserId(requestedById)
                .status(ApprovalStatus.PENDING_APPROVAL)
                .build();

        ApprovalRequest saved = approvalRequestRepository.save(request);
        log.info("[Approval] Talep oluşturuldu. id={} referenceType={} referenceId={} userId={}",
                saved.getId(), referenceType, referenceId, requestedById);
        approvalEventFactory.publishCreated(saved);
        return saved;
    }

    // -----------------------------------------------------------------------
    // Yönetici tarafından çağrılır
    // -----------------------------------------------------------------------

    /**
     * Bekleyen bir onay talebini onaylar.
     *
     * @param approvalRequestId Onay talebi ID'si
     * @param reviewerNote      Yönetici notu (opsiyonel)
     */
    @Transactional
    public ApprovalRequest approve(Long approvalRequestId, String reviewerNote) {
        Long reviewerId = TenantContextHolder.getUserId();
        ApprovalRequest request = requirePending(approvalRequestId);

        request.setStatus(ApprovalStatus.APPROVED);
        request.setApprovedByUserId(reviewerId);
        request.setApprovedAt(OffsetDateTime.now());
        request.setReviewerNote(reviewerNote);
        request.setUpdatedAt(OffsetDateTime.now());

        ApprovalRequest saved = approvalRequestRepository.save(request);
        log.info("[Approval] Onaylandı. id={} approvedBy={}", approvalRequestId, reviewerId);
        approvalEventFactory.publishResolved(saved);
        return saved;
    }

    /**
     * Bekleyen bir onay talebini reddeder.
     *
     * @param approvalRequestId Onay talebi ID'si
     * @param reviewerNote      Red gerekçesi
     */
    @Transactional
    public ApprovalRequest reject(Long approvalRequestId, String reviewerNote) {
        Long reviewerId = TenantContextHolder.getUserId();
        ApprovalRequest request = requirePending(approvalRequestId);

        request.setStatus(ApprovalStatus.REJECTED);
        request.setApprovedByUserId(reviewerId);
        request.setApprovedAt(OffsetDateTime.now());
        request.setReviewerNote(reviewerNote);
        request.setUpdatedAt(OffsetDateTime.now());

        ApprovalRequest saved = approvalRequestRepository.save(request);
        log.info("[Approval] Reddedildi. id={} rejectedBy={} note={}", approvalRequestId, reviewerId, reviewerNote);
        approvalEventFactory.publishResolved(saved);
        return saved;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private ApprovalRequest requirePending(Long approvalRequestId) {
        ApprovalRequest request = approvalRequestRepository.findById(approvalRequestId)
                .orElseThrow(() -> new BusinessException(
                        "Onay talebi bulunamadı. id=" + approvalRequestId,
                        HttpStatus.NOT_FOUND,
                        "APPROVAL_REQUEST_NOT_FOUND"));

        if (request.getStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "Bu onay talebi zaten işleme alınmış. status=" + request.getStatus(),
                    HttpStatus.CONFLICT,
                    "APPROVAL_REQUEST_ALREADY_RESOLVED");
        }

        Long companyId = TenantContextHolder.getCompanyId();
        Long locationId = TenantContextHolder.getLocationId();
        if (request.getCompanyId() != null && !request.getCompanyId().equals(companyId)) {
            throw new BusinessException(
                    "Onay talebi başka şirkete ait.",
                    HttpStatus.FORBIDDEN,
                    "APPROVAL_TENANT_MISMATCH");
        }
        if (request.getLocationId() != null && !request.getLocationId().equals(locationId)) {
            throw new BusinessException(
                    "Onay talebi başka depoya ait.",
                    HttpStatus.FORBIDDEN,
                    "APPROVAL_TENANT_MISMATCH");
        }
        return request;
    }
}

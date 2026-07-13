package com.wms.core.repository;

import com.wms.core.entity.ApprovalRequest;
import com.wms.core.entity.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    /** Belirli bir referans (nesne) için açık onay talebi var mı? */
    Optional<ApprovalRequest> findByReferenceTypeAndReferenceIdAndStatus(
            String referenceType, Long referenceId, ApprovalStatus status);

    /** Bir yöneticinin onaylaması gereken bekleyen talepleri listeler. */
    List<ApprovalRequest> findByStatusOrderByCreatedAtAsc(ApprovalStatus status);

    List<ApprovalRequest> findByStatusAndCompanyIdAndLocationIdOrderByCreatedAtAsc(
            ApprovalStatus status, Long companyId, Long locationId);

    /** Belirli bir adım konfigürasyonuna ait tüm talepleri getirir. */
    List<ApprovalRequest> findByStepConfigIdOrderByCreatedAtDesc(Long stepConfigId);
}

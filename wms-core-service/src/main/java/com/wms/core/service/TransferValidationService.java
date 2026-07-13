package com.wms.core.service;

import com.wms.core.dto.TransferRequestDto;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.LocationRepository;
import com.wms.core.repository.UserAccessRepository;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;


/**
 * Depolar arası transfer işlemlerinde çapraz yetki doğrulaması yapan servis.
 *
 * <p>Doğrulama kuralları:</p>
 * <ol>
 *   <li>Kaynak ve hedef lokasyon aynı olamaz</li>
 *   <li>Aktif context lokasyonu kaynak depo ile eşleşmelidir</li>
 *   <li>Kullanıcının kaynak lokasyona UserAccess yetkisi olmalıdır</li>
 *   <li>Kullanıcının hedef lokasyona UserAccess yetkisi olmalıdır</li>
 *   <li>Her iki lokasyon da aktif ve aynı şirkete ait olmalıdır</li>
 * </ol>
 */
@Service
public class TransferValidationService {

    private static final Logger log = LoggerFactory.getLogger(TransferValidationService.class);

    private final UserAccessRepository userAccessRepository;
    private final LocationRepository locationRepository;

    public TransferValidationService(UserAccessRepository userAccessRepository,
                                     LocationRepository locationRepository) {
        this.userAccessRepository = userAccessRepository;
        this.locationRepository = locationRepository;
    }

    /**
     * Transfer isteğinin tüm iş kurallarını doğrular.
     *
     * @param request transfer isteği DTO'su
     * @throws BusinessException doğrulama başarısız olursa
     */
    public void validateTransferAccess(TransferRequestDto request) {
        TenantContext ctx = TenantContextHolder.require();

        Long sourceLocationId = request.sourceLocationId();
        Long targetLocationId = request.targetLocationId();
        Long userId    = ctx.userId();
        Long companyId = ctx.companyId();

        // 1. Kaynak ve hedef aynı olamaz
        if (sourceLocationId.equals(targetLocationId)) {
            throw new BusinessException(
                    "Source and target locations cannot be the same.",
                    HttpStatus.BAD_REQUEST,
                    "TRANSFER_SAME_LOCATION");
        }

        // 2. Context lokasyonu = kaynak depo olmalı
        if (!ctx.locationId().equals(sourceLocationId)) {
            throw new BusinessException(
                    "Active location context does not match the source location. "
                    + "Switch to the source location before initiating a transfer.",
                    HttpStatus.FORBIDDEN,
                    "TRANSFER_CONTEXT_MISMATCH");
        }

        // 3. Kaynak lokasyon aktif ve bu şirkete ait mi?
        validateLocationBelongsToCompany(sourceLocationId, companyId, "Source");

        // 4. Hedef lokasyon aktif ve bu şirkete ait mi?
        validateLocationBelongsToCompany(targetLocationId, companyId, "Target");

        // 5. Kullanıcının kaynak lokasyona yetkisi var mı?
        if (!userAccessRepository.hasAccess(userId, companyId, sourceLocationId)) {
            throw new BusinessException(
                    "You do not have access to the source location.",
                    HttpStatus.FORBIDDEN,
                    "TRANSFER_NO_SOURCE_ACCESS");
        }

        // 6. Kullanıcının hedef lokasyona yetkisi var mı?
        if (!userAccessRepository.hasAccess(userId, companyId, targetLocationId)) {
            throw new BusinessException(
                    "You do not have access to the target location.",
                    HttpStatus.FORBIDDEN,
                    "TRANSFER_NO_TARGET_ACCESS");
        }

        log.info("Transfer access validated — userId={}, source={}, target={}",
                userId, sourceLocationId, targetLocationId);
    }

    /**
     * Lokasyonun mevcut, aktif ve belirtilen şirkete ait olduğunu doğrular.
     */
    private void validateLocationBelongsToCompany(Long locationId, Long companyId, String label) {
        locationRepository.findById(locationId)
                .filter(loc -> loc.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new BusinessException(
                        label + " location not found or does not belong to the current company.",
                        HttpStatus.BAD_REQUEST,
                        "TRANSFER_INVALID_LOCATION"));
    }
}

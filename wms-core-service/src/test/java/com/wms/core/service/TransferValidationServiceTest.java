package com.wms.core.service;

import com.wms.core.dto.TransferRequestDto;
import com.wms.core.entity.Location;
import com.wms.core.entity.Company;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.LocationRepository;
import com.wms.core.repository.UserAccessRepository;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferValidationServiceTest {

    @Mock
    private UserAccessRepository userAccessRepository;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private TransferValidationService transferValidationService;

    private final Long userId = 1L;
    private final Long companyId = 1L;
    private final Long sourceLocationId = 1L;
    private final Long targetLocationId = 2L;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setContext(new TenantContext(userId, companyId, sourceLocationId));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void validateTransferAccess_succeedsWhenAllRulesPass() {
        mockLocationsBelongToCompany();
        when(userAccessRepository.hasAccess(userId, companyId, sourceLocationId)).thenReturn(true);
        when(userAccessRepository.hasAccess(userId, companyId, targetLocationId)).thenReturn(true);

        TransferRequestDto request = new TransferRequestDto(
                sourceLocationId, targetLocationId, "SKU-001", 10, null);

        assertThatCode(() -> transferValidationService.validateTransferAccess(request))
                .doesNotThrowAnyException();
    }

    @Test
    void validateTransferAccess_failsWhenSourceEqualsTarget() {
        TransferRequestDto request = new TransferRequestDto(
                sourceLocationId, sourceLocationId, "SKU-001", 10, null);

        assertThatThrownBy(() -> transferValidationService.validateTransferAccess(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assert be.getStatus() == HttpStatus.BAD_REQUEST;
                    assert "TRANSFER_SAME_LOCATION".equals(be.getErrorCode());
                });
    }

    @Test
    void validateTransferAccess_failsWhenContextLocationDoesNotMatchSource() {
        Long differentSource = 1L;
        TransferRequestDto request = new TransferRequestDto(
                differentSource, targetLocationId, "SKU-001", 10, null);

        assertThatThrownBy(() -> transferValidationService.validateTransferAccess(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assert be.getStatus() == HttpStatus.FORBIDDEN;
                    assert "TRANSFER_CONTEXT_MISMATCH".equals(be.getErrorCode());
                });
    }

    @Test
    void validateTransferAccess_failsWhenNoTargetAccess() {
        mockLocationsBelongToCompany();
        when(userAccessRepository.hasAccess(userId, companyId, sourceLocationId)).thenReturn(true);
        when(userAccessRepository.hasAccess(userId, companyId, targetLocationId)).thenReturn(false);

        TransferRequestDto request = new TransferRequestDto(
                sourceLocationId, targetLocationId, "SKU-001", 10, null);

        assertThatThrownBy(() -> transferValidationService.validateTransferAccess(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assert be.getStatus() == HttpStatus.FORBIDDEN;
                    assert "TRANSFER_NO_TARGET_ACCESS".equals(be.getErrorCode());
                });
    }

    private void mockLocationsBelongToCompany() {
        Company company = new Company();
        company.setId(companyId);

        Location source = new Location();
        source.setId(sourceLocationId);
        source.setCompany(company);

        Location target = new Location();
        target.setId(targetLocationId);
        target.setCompany(company);

        when(locationRepository.findById(sourceLocationId)).thenReturn(Optional.of(source));
        when(locationRepository.findById(targetLocationId)).thenReturn(Optional.of(target));
    }
}

package com.wms.finance.service;

import com.wms.finance.dto.TaxRateUpdateRequest;
import com.wms.finance.entity.TaxRate;
import com.wms.finance.entity.TaxRateAuditLog;
import com.wms.finance.entity.TaxType;
import com.wms.finance.entity.enums.TaxRateAuditActionType;
import com.wms.finance.exception.TaxRateConflictException;
import com.wms.finance.exception.TaxRateNotFoundException;
import com.wms.finance.repository.TaxRateAuditLogRepository;
import com.wms.finance.repository.TaxRateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaxRateVersioningService")
class TaxRateVersioningServiceTest {

    @Mock TaxRateRepository taxRateRepository;
    @Mock TaxRateAuditLogRepository auditLogRepository;

    @InjectMocks TaxRateVersioningService service;

    private static final Long RATE_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final LocalDate START = LocalDate.of(2025, 1, 1);
    private static final LocalDate EFFECTIVE = LocalDate.now().plusDays(7);

    @Test
    @DisplayName("Temporal güncelleme: eski kaydı kapatır, yeni satır ve audit oluşturur")
    void updateRate_versionsTemporalRecord() {
        TaxType taxType = TaxType.builder().id(1L).code("KDV").name("KDV").active(true).build();
        TaxRate existing = TaxRate.builder()
                .id(RATE_ID)
                .taxType(taxType)
                .countryId(1L)
                .rate(new BigDecimal("20.00"))
                .startDate(START)
                .active(true)
                .build();

        when(taxRateRepository.findById(RATE_ID)).thenReturn(Optional.of(existing));
        when(taxRateRepository.existsOverlappingRateExcluding(any(), any(), any(), any(), any(), any(),
                eq(EFFECTIVE), any(), eq(RATE_ID))).thenReturn(false);
        when(taxRateRepository.save(any(TaxRate.class))).thenAnswer(inv -> {
            TaxRate r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(1L);
            }
            return r;
        });

        var request = new TaxRateUpdateRequest(RATE_ID, new BigDecimal("18.00"), EFFECTIVE);
        var result = service.updateRate(request, USER_ID);

        assertThat(result.expiredRate().endDate()).isEqualTo(EFFECTIVE.minusDays(1));
        assertThat(result.newRate().rate()).isEqualByComparingTo("18.00");
        assertThat(result.newRate().startDate()).isEqualTo(EFFECTIVE);
        assertThat(existing.getEndDate()).isEqualTo(EFFECTIVE.minusDays(1));
        assertThat(existing.isActive()).isTrue();

        verify(taxRateRepository).flush();

        ArgumentCaptor<TaxRateAuditLog> auditCaptor = ArgumentCaptor.forClass(TaxRateAuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getActionType()).isEqualTo(TaxRateAuditActionType.UPDATE_RATE);
        assertThat(auditCaptor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("Kayıt bulunamazsa TaxRateNotFoundException")
    void updateRate_notFound() {
        when(taxRateRepository.findById(RATE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRate(
                new TaxRateUpdateRequest(RATE_ID, new BigDecimal("18.00"), EFFECTIVE), USER_ID))
                .isInstanceOf(TaxRateNotFoundException.class);
    }

    @Test
    @DisplayName("Çakışan aralık varsa TaxRateConflictException")
    void updateRate_conflict() {
        TaxType taxType = TaxType.builder().id(1L).code("KDV").name("KDV").active(true).build();
        TaxRate existing = TaxRate.builder()
                .id(RATE_ID)
                .taxType(taxType)
                .countryId(1L)
                .rate(new BigDecimal("20.00"))
                .startDate(START)
                .active(true)
                .build();

        when(taxRateRepository.findById(RATE_ID)).thenReturn(Optional.of(existing));
        when(taxRateRepository.existsOverlappingRateExcluding(any(), any(), any(), any(), any(), any(),
                eq(EFFECTIVE), any(), eq(RATE_ID))).thenReturn(true);

        assertThatThrownBy(() -> service.updateRate(
                new TaxRateUpdateRequest(RATE_ID, new BigDecimal("18.00"), EFFECTIVE), USER_ID))
                .isInstanceOf(TaxRateConflictException.class);

        verify(taxRateRepository, never()).save(any());
    }
}

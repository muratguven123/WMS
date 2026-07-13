package com.wms.core.service;

import com.wms.core.entity.Location;
import com.wms.core.entity.TransactionLog;
import com.wms.core.repository.LocationRepository;
import com.wms.core.repository.TransactionLogRepository;
import com.wms.core.repository.spec.TransactionLogSpecifications;
import com.wms.core.util.timezone.DateRangeUtcQueryHelper;
import com.wms.core.util.timezone.InstantRange;
import com.wms.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionLogReportServiceTest {

    @Mock
    private TransactionLogRepository transactionLogRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private DateRangeUtcQueryHelper dateRangeUtcQueryHelper;

    @InjectMocks
    private TransactionLogReportService reportService;

    private Long companyId;
    private Long locationId;

    @BeforeEach
    void setUp() {
        companyId = 1L;
        locationId = 1L;
    }

    @Test
    void findByLocalDateRange_usesUtcRangeFromLocationTimezone() {
        LocalDate reportDate = LocalDate.of(2026, Month.JANUARY, 10);
        InstantRange utcRange = new InstantRange(
                java.time.Instant.parse("2026-01-09T21:00:00Z"),
                java.time.Instant.parse("2026-01-10T20:59:59.999999999Z"));

        Location location = new Location();
        location.setTimezone("Europe/Istanbul");
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(dateRangeUtcQueryHelper.convertToUtcRange(reportDate, reportDate, "Europe/Istanbul"))
                .thenReturn(utcRange);
        when(transactionLogRepository.findAll(any(Specification.class))).thenReturn(List.of(new TransactionLog()));

        List<TransactionLog> results = reportService.findByLocalDateRange(
                companyId, locationId, reportDate, reportDate);

        assertThat(results).hasSize(1);

        ArgumentCaptor<Specification<TransactionLog>> specCaptor =
                ArgumentCaptor.forClass(Specification.class);
        verify(transactionLogRepository).findAll(specCaptor.capture());

        verify(dateRangeUtcQueryHelper).convertToUtcRange(reportDate, reportDate, "Europe/Istanbul");
        assertThat(specCaptor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("Lokasyon bulunamazsa BusinessException fırlatır ve sorgu çalıştırılmaz")
    void findByLocalDateRange_unknownLocation_throwsWithoutQuery() {
        LocalDate reportDate = LocalDate.of(2026, Month.JANUARY, 10);
        when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                reportService.findByLocalDateRange(companyId, locationId, reportDate, reportDate))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(locationId.toString());

        verify(dateRangeUtcQueryHelper, never()).convertToUtcRange(any(), any(), any());
        verify(transactionLogRepository, never()).findAll(any(Specification.class));
    }
}

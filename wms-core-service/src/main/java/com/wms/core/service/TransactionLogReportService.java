package com.wms.core.service;

import com.wms.core.entity.Location;
import com.wms.core.entity.TransactionLog;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.LocationRepository;
import com.wms.core.repository.TransactionLogRepository;
import com.wms.core.repository.spec.TransactionLogSpecifications;
import com.wms.core.util.timezone.DateRangeUtcQueryHelper;
import com.wms.core.util.timezone.InstantRange;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Tarih bazlı transaction log raporlama servisi.
 *
 * <p>UI'dan gelen yerel tarih filtresini {@link DateRangeUtcQueryHelper} ile UTC'ye
 * çevirir; {@link TransactionLogSpecifications} üzerinden sorgular.</p>
 */
@Service
@RequiredArgsConstructor
public class TransactionLogReportService {

    private final TransactionLogRepository transactionLogRepository;
    private final LocationRepository locationRepository;
    private final DateRangeUtcQueryHelper dateRangeUtcQueryHelper;

    /**
     * Belirtilen lokasyonun yerel tarih aralığındaki işlem kayıtlarını döner.
     *
     * @param companyId  şirket Long
     * @param locationId depo Long (timezone kaynağı)
     * @param startDate  yerel başlangıç tarihi (dahil)
     * @param endDate    yerel bitiş tarihi (dahil)
     */
    @Transactional(readOnly = true)
    public List<TransactionLog> findByLocalDateRange(Long companyId,
                                                      Long locationId,
                                                      LocalDate startDate,
                                                      LocalDate endDate) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new BusinessException(
                        "Lokasyon bulunamadı: " + locationId, HttpStatus.NOT_FOUND));

        InstantRange utcRange = dateRangeUtcQueryHelper.convertToUtcRange(
                startDate, endDate, location.getTimezone());

        return transactionLogRepository.findAll(
                TransactionLogSpecifications.reportFilter(companyId, locationId, null, utcRange));
    }
}

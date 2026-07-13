package com.wms.core.repository.spec;

import com.wms.core.entity.TransactionLog;
import com.wms.core.util.timezone.InstantRange;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link TransactionLog} için JPA Specification fabrika sınıfı.
 *
 * <p>Tüm tarih aralığı sorguları {@link InstantRange} üzerinden yapılır;
 * bu sayede UI'dan gelen yerel tarih filtresi UTC'ye doğru dönüştürülmüş
 * olarak sorguya girer ve gün kayması (date-shift) oluşmaz.</p>
 *
 * <h3>Kullanım örneği</h3>
 * <pre>
 * // Kullanıcı arayüzden gelen filtre
 * LocalDate reportDate = LocalDate.of(2026, 1, 10);
 * String timezone = location.getTimezone(); // "Europe/Istanbul"
 *
 * InstantRange utcRange = dateRangeUtcQueryHelper
 *         .convertToUtcRange(reportDate, reportDate, timezone);
 *
 * Specification&lt;TransactionLog&gt; spec = TransactionLogSpecifications
 *         .withinUtcRange(utcRange)
 *         .and(TransactionLogSpecifications.byLocation(locationId))
 *         .and(TransactionLogSpecifications.byCompany(companyId));
 *
 * List&lt;TransactionLog&gt; results = transactionLogRepository.findAll(spec);
 * </pre>
 */
public final class TransactionLogSpecifications {

    private TransactionLogSpecifications() {}

    // -------------------------------------------------------------------------
    // Tekil filtreler
    // -------------------------------------------------------------------------

    /**
     * {@code createdAtUtc} alanını UTC {@link InstantRange} sınırları içinde filtreler.
     *
     * <p>Sınır değerleri dahildir ({@code >=} start, {@code <=} end).
     * {@code InstantRange} doğrudan {@code Instant} taşır; JPA'ya
     * {@code OffsetDateTime} (UTC) olarak iletilir — Hibernate TIMESTAMPTZ
     * ile kayıpsız eşleştirir.</p>
     */
    public static Specification<TransactionLog> withinUtcRange(InstantRange range) {
        return (root, query, cb) -> {
            OffsetDateTime start = range.start().atOffset(ZoneOffset.UTC);
            OffsetDateTime end   = range.end().atOffset(ZoneOffset.UTC);
            return cb.between(root.get("createdAtUtc"), start, end);
        };
    }

    /** Belirtilen lokasyona ait kayıtları filtreler. */
    public static Specification<TransactionLog> byLocation(Long locationId) {
        return (root, query, cb) ->
                cb.equal(root.get("location").get("id"), locationId);
    }

    /** Belirtilen şirkete ait kayıtları filtreler. */
    public static Specification<TransactionLog> byCompany(Long companyId) {
        return (root, query, cb) ->
                cb.equal(root.get("company").get("id"), companyId);
    }

    /** Belirtilen action type'a sahip kayıtları filtreler. */
    public static Specification<TransactionLog> byActionType(String actionType) {
        return (root, query, cb) ->
                cb.equal(root.get("actionType"), actionType);
    }

    // -------------------------------------------------------------------------
    // Birleşik filtre — raporlama katmanı için kolaylık metodu
    // -------------------------------------------------------------------------

    /**
     * Raporlama sorgularında sık kullanılan filtre kombinasyonunu tek metotta sunar.
     *
     * <p>Tüm parametreler opsiyoneldir; null geçilenler sorguya dahil edilmez.</p>
     *
     * @param companyId  Şirket filtresi (null → atlanır)
     * @param locationId Lokasyon filtresi (null → atlanır)
     * @param actionType İşlem tipi filtresi (null → atlanır)
     * @param range      UTC aralık filtresi (null → atlanır)
     */
    public static Specification<TransactionLog> reportFilter(Long companyId,
                                                              Long locationId,
                                                              String actionType,
                                                              InstantRange range) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (companyId != null) {
                predicates.add(cb.equal(root.get("company").get("id"), companyId));
            }
            if (locationId != null) {
                predicates.add(cb.equal(root.get("location").get("id"), locationId));
            }
            if (actionType != null && !actionType.isBlank()) {
                predicates.add(cb.equal(root.get("actionType"), actionType));
            }
            if (range != null) {
                OffsetDateTime start = range.start().atOffset(ZoneOffset.UTC);
                OffsetDateTime end   = range.end().atOffset(ZoneOffset.UTC);
                predicates.add(cb.between(root.get("createdAtUtc"), start, end));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}

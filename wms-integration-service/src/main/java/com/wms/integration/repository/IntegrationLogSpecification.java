package com.wms.integration.repository;

import com.wms.integration.api.dto.IntegrationLogFilter;
import com.wms.integration.entity.IntegrationLog;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link IntegrationLog} için dinamik JPA Specification.
 *
 * <p>Her filtre alanı null kontrolüyle eklenir; null alan WHERE clause'a dahil
 * edilmez. Bu yaklaşım N adet farklı sorgu metodu yazmaktan kaçınır.
 *
 * <p>JOIN'ler FETCH ile yazılarak N+1 sorunu önlenir.
 */
public class IntegrationLogSpecification implements Specification<IntegrationLog> {

    private final IntegrationLogFilter filter;

    public IntegrationLogSpecification(IntegrationLogFilter filter) {
        this.filter = filter;
    }

    @Override
    public Predicate toPredicate(Root<IntegrationLog> root,
                                 CriteriaQuery<?> query,
                                 CriteriaBuilder cb) {

        // Sayfalı sorgularda count query için FETCH yapılmaz
        if (query.getResultType() != Long.class && query.getResultType() != long.class) {
            root.fetch("locationIntegrationConfig", JoinType.LEFT)
                    .fetch("integrationSystem", JoinType.LEFT);
            root.fetch("integrationJob", JoinType.LEFT);
        }

        List<Predicate> predicates = new ArrayList<>();

        // --- status filtresi ---
        if (filter.getStatus() != null) {
            predicates.add(cb.equal(root.get("status"), filter.getStatus()));
        }

        // --- locationId filtresi (LocationIntegrationConfig üzerinden) ---
        if (filter.getLocationId() != null) {
            Join<?, ?> config = getOrJoin(root, "locationIntegrationConfig");
            predicates.add(cb.equal(config.get("locationId"), filter.getLocationId()));
        }

        // --- jobCode filtresi (IntegrationJob üzerinden) ---
        if (filter.getJobCode() != null && !filter.getJobCode().isBlank()) {
            Join<?, ?> job = getOrJoin(root, "integrationJob");
            predicates.add(cb.equal(job.get("code"), filter.getJobCode()));
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }

    /** Mevcut JOIN varsa onu döner, yoksa yeni JOIN oluşturur (duplicate önleme). */
    @SuppressWarnings("unchecked")
    private Join<?, ?> getOrJoin(Root<IntegrationLog> root, String attribute) {
        return root.getJoins().stream()
                .filter(j -> j.getAttribute().getName().equals(attribute))
                .findFirst()
                .orElseGet(() -> root.join(attribute, JoinType.LEFT));
    }
}

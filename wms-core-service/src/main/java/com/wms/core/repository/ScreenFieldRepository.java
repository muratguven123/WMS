package com.wms.core.repository;

import com.wms.core.entity.ScreenField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link ScreenField} entity'si için Spring Data JPA repository.
 */
@Repository
public interface ScreenFieldRepository extends JpaRepository<ScreenField, Long> {

    /**
     * Belirtilen ekrana ait tüm aktif alanları getirir.
     * Kural motoru her form şeması sorgusunda bu metodu çağırır.
     *
     * @param screenId ekran Long'si
     * @return alan listesi
     */
    List<ScreenField> findByScreenId(Long screenId);

    /**
     * Ekran kodu üzerinden alanları doğrudan çeker (JOIN Screen).
     * Cache key hesaplamasında {@code screenCode} kullanıldığı için ayrı overload vardır.
     *
     * @param screenCode ekran kodu, örn: {@code MAT_CARD_FORM}
     * @return alan listesi
     */
    @Query("""
            SELECT sf FROM ScreenField sf
            JOIN FETCH sf.screen s
            WHERE s.code = :screenCode
              AND sf.isActive = true
            ORDER BY sf.fieldKey
            """)
    List<ScreenField> findByScreenCode(@Param("screenCode") String screenCode);

    @Query("""
            SELECT sf FROM ScreenField sf
            JOIN FETCH sf.screen
            WHERE sf.id = :id
            """)
    java.util.Optional<ScreenField> findByIdWithScreen(@Param("id") Long id);
}

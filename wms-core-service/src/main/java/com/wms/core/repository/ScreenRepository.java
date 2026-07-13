package com.wms.core.repository;

import com.wms.core.entity.Screen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link Screen} entity'si için Spring Data JPA repository.
 *
 * <p>{@code @SQLRestriction("is_active = true")} entity üzerinde tanımlı olduğundan
 * tüm sorgular otomatik olarak aktif kayıtlarla çalışır.</p>
 */
@Repository
public interface ScreenRepository extends JpaRepository<Screen, Long> {

    /**
     * Frontend form kodu (örn: {@code REC_CONTROL_FORM}) ile ekranı getirir.
     *
     * @param code ekran kodu
     * @return aktif Screen kaydı
     */
    Optional<Screen> findByCode(String code);

    @Query("""
            SELECT DISTINCT s FROM Screen s
            LEFT JOIN FETCH s.fields
            WHERE s.code = :code
            """)
    Optional<Screen> findByCodeWithFields(@Param("code") String code);

    /**
     * Belirtilen kodlu aktif ekranın var olup olmadığını kontrol eder.
     * Kural yönetiminde kod çakışması doğrulamasında kullanılır.
     *
     * @param code ekran kodu
     * @return true → kayıt mevcut
     */
    boolean existsByCode(String code);
}

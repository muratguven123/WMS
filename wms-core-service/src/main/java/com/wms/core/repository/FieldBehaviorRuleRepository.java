package com.wms.core.repository;

import com.wms.core.entity.FieldBehaviorRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link FieldBehaviorRule} entity'si için Spring Data JPA repository.
 *
 * <p>Bu entity soft-delete kullanmaz — silinme işlemleri hard-delete'tir.
 * Değişiklik geçmişi {@link com.wms.core.entity.ConfigurationAuditLog} üzerinden izlenir.</p>
 */
@Repository
public interface FieldBehaviorRuleRepository extends JpaRepository<FieldBehaviorRule, Long> {

    /**
     * Belirtilen ekrana ({@code screenCode}) ait tüm aktif alanların tüm kurallarını
     * tek sorguda çeker. N+1 problemini önlemek için JPQL JOIN kullanılır.
     *
     * <p>Kural motoru bu listeyi in-memory filtreler; DB tarafında context
     * parametresi uygulanmaz çünkü nullable koşullar DB-level filtreyi
     * karmaşıklaştırır ve cache verimliliğini düşürür.</p>
     *
     * @param screenCode ekran kodu
     * @return kural listesi, öncelik büyükten küçüğe sıralı
     */
    @Query("""
            SELECT r FROM FieldBehaviorRule r
            JOIN r.screenField sf
            JOIN sf.screen s
            WHERE s.code = :screenCode
              AND sf.isActive = true
            ORDER BY r.priority DESC
            """)
    List<FieldBehaviorRule> findAllByScreenCode(@Param("screenCode") String screenCode);

    /**
     * Belirli bir alan ({@code screenFieldId}) için tüm kuralları getirir.
     * Kural yönetim paneli ve çakışma kontrolünde kullanılır.
     *
     * @param screenFieldId alan Long'si
     * @return kural listesi
     */
    List<FieldBehaviorRule> findByScreenFieldId(Long screenFieldId);

    @Query("""
            SELECT r FROM FieldBehaviorRule r
            JOIN FETCH r.screenField sf
            JOIN FETCH sf.screen
            WHERE r.id = :id
            """)
    java.util.Optional<FieldBehaviorRule> findByIdWithScreenField(@Param("id") Long id);

    /**
     * Belirtilen ekrana ait tüm kuralların {@code screenFieldId} listesini döner.
     * Cache eviction sırasında silinecek key kümesini hesaplamada kullanılır.
     *
     * @param screenCode ekran kodu
     * @return screenField Long'leri
     */
    @Query("""
            SELECT DISTINCT r.screenField.id FROM FieldBehaviorRule r
            JOIN r.screenField sf
            JOIN sf.screen s
            WHERE s.code = :screenCode
            """)
    List<Long> findScreenFieldIdsByScreenCode(@Param("screenCode") String screenCode);

    /**
     * Aynı alan + aynı öncelik + bağlam kombinasyonu için kural var mı kontrol eder.
     * Çakışma (conflict) tespitinde kural yönetim servisi bu metodu kullanır.
     *
     * @param screenFieldId alan Long'si
     * @param priority      kontrol edilecek öncelik değeri
     * @param excludeId     güncelleme senaryosunda mevcut kuralı hariç tutmak için (null → hariç tutma yok)
     * @return true → çakışma var
     */
    @Query("""
            SELECT COUNT(r) > 0 FROM FieldBehaviorRule r
            WHERE r.screenField.id = :screenFieldId
              AND r.priority = :priority
              AND (:excludeId IS NULL OR r.id <> :excludeId)
            """)
    boolean existsConflict(
            @Param("screenFieldId") Long screenFieldId,
            @Param("priority") int priority,
            @Param("excludeId") Long excludeId
    );

    /**
     * Bir alana ait tüm kuralları siler (hard-delete).
     * Alan devre dışı bırakılırken veya migration sırasında kullanılır.
     *
     * @param screenFieldId alan Long'si
     */
    @Modifying
    @Query("DELETE FROM FieldBehaviorRule r WHERE r.screenField.id = :screenFieldId")
    void deleteAllByScreenFieldId(@Param("screenFieldId") Long screenFieldId);
}

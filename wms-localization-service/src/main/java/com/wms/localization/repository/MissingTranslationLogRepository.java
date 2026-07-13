package com.wms.localization.repository;

import com.wms.localization.entity.MissingTranslationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface MissingTranslationLogRepository extends JpaRepository<MissingTranslationLog, Long> {

    /** (locale, keyCode) çifti daha önce loglandı mı? — upsert öncesi kontrol */
    Optional<MissingTranslationLog> findByLocaleAndKeyCode(String locale, String keyCode);

    /** Admin paneli: çözümlenmemiş eksikler — sayfalı */
    Page<MissingTranslationLog> findByResolvedFalseOrderByHitCountDesc(Pageable pageable);

    /** Admin paneli: belirli locale'ın çözümlenmemiş eksikleri */
    Page<MissingTranslationLog> findByLocaleAndResolvedFalseOrderByHitCountDesc(
            String locale, Pageable pageable);

    /**
     * Çeviri tamamlandığında ilgili log kaydını çözümlendi olarak işaretle.
     * Import servisi veya admin paneli tarafından çağrılır.
     */
    @Modifying
    @Query("""
            UPDATE MissingTranslationLog m
            SET m.resolved = true, m.resolvedAt = :now
            WHERE m.locale = :locale AND m.keyCode = :keyCode
            """)
    int resolveByLocaleAndKeyCode(
            @Param("locale")  String locale,
            @Param("keyCode") String keyCode,
            @Param("now")     LocalDateTime now);

    /**
     * Belirli bir locale'ın tüm loglarını temizle.
     * Dil silindiğinde veya toplu reset'te kullanılır.
     */
    @Modifying
    @Query("DELETE FROM MissingTranslationLog m WHERE m.locale = :locale")
    void deleteAllByLocale(@Param("locale") String locale);

    /** Toplam çözümlenmemiş eksik sayısı — dashboard için */
    long countByResolvedFalse();
}

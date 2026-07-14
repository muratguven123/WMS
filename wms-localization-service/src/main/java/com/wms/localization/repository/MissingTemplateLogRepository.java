package com.wms.localization.repository;

import com.wms.localization.entity.MissingTemplateLog;
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
public interface MissingTemplateLogRepository extends JpaRepository<MissingTemplateLog, Long> {

    /** (locale, templateCode) çifti daha önce loglandı mı? — upsert öncesi kontrol */
    Optional<MissingTemplateLog> findByLocaleAndTemplateCode(String locale, String templateCode);

    /** Admin paneli: çözümlenmemiş eksikler — sayfalı */
    Page<MissingTemplateLog> findByResolvedFalseOrderByHitCountDesc(Pageable pageable);

    /**
     * İçerik upsert edildiğinde ilgili log kaydını çözümlendi olarak işaretle.
     * NotificationTemplateAdminService ve import servisi tarafından çağrılır.
     */
    @Modifying
    @Query("""
            UPDATE MissingTemplateLog m
            SET m.resolved = true, m.resolvedAt = :now
            WHERE m.locale = :locale AND m.templateCode = :templateCode
            """)
    int resolveByLocaleAndTemplateCode(
            @Param("locale")       String locale,
            @Param("templateCode") String templateCode,
            @Param("now")          LocalDateTime now);
}

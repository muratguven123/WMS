package com.wms.localization.repository;

import com.wms.localization.entity.NotificationTemplateContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTemplateContentRepository
        extends JpaRepository<NotificationTemplateContent, Long> {

    /** Tek dil içeriği — render ve upsert öncesi kontrol için. */
    Optional<NotificationTemplateContent> findByTemplate_IdAndLanguageCode(
            Long templateId, String languageCode);

    /** Bir şablonun tüm dil içerikleri — admin detay ekranı için. */
    List<NotificationTemplateContent> findAllByTemplate_IdOrderByLanguageCodeAsc(Long templateId);

    /**
     * Tüm içerikleri şablonuyla birlikte getir (export + kapsama raporu).
     * JOIN FETCH ile N+1 önlenir.
     */
    @Query("""
            SELECT c FROM NotificationTemplateContent c
            JOIN FETCH c.template t
            ORDER BY t.templateCode, c.languageCode
            """)
    List<NotificationTemplateContent> findAllWithTemplate();

    /**
     * Şablon silinirken içeriklerin de temizlenmesi için.
     * (DB tarafında ON DELETE CASCADE da vardır — çift güvence.)
     */
    @Modifying
    @Query("DELETE FROM NotificationTemplateContent c WHERE c.template.id = :templateId")
    void deleteAllByTemplateId(@Param("templateId") Long templateId);
}

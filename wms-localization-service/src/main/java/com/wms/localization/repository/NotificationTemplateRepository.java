package com.wms.localization.repository;

import com.wms.localization.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    /** Şablon kodu ile bul — admin ekranları için (aktif/pasif fark etmez). */
    Optional<NotificationTemplate> findByTemplateCode(String templateCode);

    /** Render akışı: yalnızca aktif şablonlar döner. */
    Optional<NotificationTemplate> findByTemplateCodeAndActiveTrue(String templateCode);

    /** Kod benzersizliği kontrolü — create öncesi kısa devre sorgusu. */
    boolean existsByTemplateCode(String templateCode);

    /** Admin listesi — koda göre alfabetik. */
    List<NotificationTemplate> findAllByOrderByTemplateCodeAsc();

    /** Kapsama raporu — yalnızca aktif şablonlar. */
    List<NotificationTemplate> findAllByActiveTrueOrderByTemplateCodeAsc();
}

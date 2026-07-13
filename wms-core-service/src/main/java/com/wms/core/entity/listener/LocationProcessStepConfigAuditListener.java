package com.wms.core.entity.listener;

import com.wms.core.entity.LocationProcessStepConfig;
import com.wms.core.entity.audit.AuditSnapshot;
import com.wms.core.event.ConfigChangeEvent;
import com.wms.core.security.TenantContextHolder;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Entity Listener — {@link LocationProcessStepConfig} üzerindeki
 * tüm güncellemeleri yakalar ve denetim olayı yayınlar.
 *
 * <h3>Çalışma prensibi</h3>
 * <ol>
 *   <li>{@link PostLoad}: Entity DB'den yüklendiğinde mevcut alan değerlerinin
 *       anlık görüntüsü ({@link AuditSnapshot}) entity üzerindeki {@code @Transient}
 *       alana kaydedilir.</li>
 *   <li>{@link PreUpdate}: Hibernate flush'tan önce tetiklenir; snapshot ile
 *       mevcut değerler karşılaştırılır. Değişen alanlar için
 *       {@link ConfigChangeEvent} yayınlanır.</li>
 * </ol>
 *
 * <h3>Thread güvenliği</h3>
 * {@code PreUpdate} orijinal istek thread'inde çalışır; bu yüzden
 * {@link TenantContextHolder} ({@code InheritableThreadLocal}) üzerinden
 * kullanıcı ID'sine güvenle erişilebilir.
 *
 * <h3>Spring injection notu</h3>
 * JPA entity listener'ları Spring tarafından değil JPA container'ı tarafından
 * oluşturulur. {@code @Autowired} field injection için Hibernate'in
 * {@code hibernate.ejb.event.post-insert} veya CDI desteği yerine
 * Spring'in {@code @Component} + {@code ApplicationContext} üzerinden
 * çözümleme yaptığı {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory}
 * kullanılır — bu konfigürasyon {@link com.wms.core.config.JpaListenerConfig}'te tanımlanır.
 */
@Slf4j
@Component
public class LocationProcessStepConfigAuditListener {

    private static ApplicationEventPublisher eventPublisher;

    /**
     * Spring context hazır olduğunda publisher'ı static alana enjekte eder.
     * JPA listener'lar Spring tarafından new ile yaratıldığından instance
     * injection çalışmaz; static field ile bu sorun aşılır.
     */
    @Autowired
    public void setEventPublisher(ApplicationEventPublisher publisher) {
        LocationProcessStepConfigAuditListener.eventPublisher = publisher;
    }

    // -----------------------------------------------------------------------
    // JPA Lifecycle Callbacks
    // -----------------------------------------------------------------------

    /**
     * Entity DB'den yüklendiğinde anlık görüntüyü kaydeder.
     * Bu sayede {@code @PreUpdate}'de "eski değer" bilgisi mevcut olur.
     */
    @PostLoad
    public void captureSnapshot(LocationProcessStepConfig entity) {
        entity.setAuditSnapshot(new AuditSnapshot(
                entity.getSequence(),
                entity.isMandatory(),
                entity.isActive(),
                entity.isRequiresApproval(),
                entity.getErrorStrategy(),
                entity.getResponsibleRoleId()
        ));
    }

    /**
     * Hibernate flush'tan önce değişen alanları tespit eder ve
     * {@link ConfigChangeEvent} yayınlar.
     */
    @PreUpdate
    public void publishChangeEvent(LocationProcessStepConfig entity) {
        AuditSnapshot snapshot = entity.getAuditSnapshot();

        if (snapshot == null) {
            // Entity uygulama içinde programatik olarak oluşturulmuş,
            // DB'den yüklenmemiş → snapshot yok, karşılaştırma yapılamaz.
            log.warn("[Audit] Snapshot bulunamadı, audit atlanıyor. entityId={}", entity.getId());
            return;
        }

        List<ConfigChangeEvent.FieldChange> changes = new ArrayList<>();

        if (snapshot.sequence() != entity.getSequence()) {
            changes.add(new ConfigChangeEvent.FieldChange(
                    "sequence",
                    String.valueOf(snapshot.sequence()),
                    String.valueOf(entity.getSequence())));
        }
        if (snapshot.mandatory() != entity.isMandatory()) {
            changes.add(new ConfigChangeEvent.FieldChange(
                    "isMandatory",
                    String.valueOf(snapshot.mandatory()),
                    String.valueOf(entity.isMandatory())));
        }
        if (snapshot.requiresApproval() != entity.isRequiresApproval()) {
            changes.add(new ConfigChangeEvent.FieldChange(
                    "requiresApproval",
                    String.valueOf(snapshot.requiresApproval()),
                    String.valueOf(entity.isRequiresApproval())));
        }
        if (snapshot.errorStrategy() != entity.getErrorStrategy()) {
            changes.add(new ConfigChangeEvent.FieldChange(
                    "errorStrategy",
                    snapshot.errorStrategy() != null ? snapshot.errorStrategy().name() : null,
                    entity.getErrorStrategy() != null  ? entity.getErrorStrategy().name()  : null));
        }
        if (!java.util.Objects.equals(snapshot.responsibleRoleId(), entity.getResponsibleRoleId())) {
            changes.add(new ConfigChangeEvent.FieldChange(
                    "responsibleRoleId",
                    snapshot.responsibleRoleId() != null ? snapshot.responsibleRoleId().toString() : null,
                    entity.getResponsibleRoleId() != null ? entity.getResponsibleRoleId().toString() : null));
        }

        // isActive değişimi → özel actionType
        boolean activeChanged = snapshot.active() != entity.isActive();
        if (activeChanged) {
            changes.add(new ConfigChangeEvent.FieldChange(
                    "isActive",
                    String.valueOf(snapshot.active()),
                    String.valueOf(entity.isActive())));
        }

        if (changes.isEmpty()) {
            log.debug("[Audit] Değişiklik tespit edilmedi, event yayınlanmıyor. entityId={}", entity.getId());
            return;
        }

        String actionType = deriveActionType(snapshot.active(), entity.isActive());
        Long changedByUserId = resolveUserId();

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new ConfigChangeEvent(
                    this,
                    LocationProcessStepConfig.class.getSimpleName(),
                    entity.getId(),
                    actionType,
                    changes,
                    changedByUserId));

            log.debug("[Audit] ConfigChangeEvent yayınlandı. entityId={} fieldCount={}",
                    entity.getId(), changes.size());
        } else {
            log.error("[Audit] ApplicationEventPublisher inject edilmedi! Audit loglanamıyor.");
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private String deriveActionType(boolean wasActive, boolean isNowActive) {
        if (wasActive && !isNowActive) return "DEACTIVATE";
        if (!wasActive && isNowActive)  return "ACTIVATE";
        return "UPDATE";
    }

    private Long resolveUserId() {
        return TenantContextHolder.getContext()
                .map(ctx -> ctx.userId())
                .orElseGet(() -> {
                    log.warn("[Audit] TenantContext bulunamadı, changedByUserId null olarak kaydedilecek.");
                    return null;
                });
    }
}

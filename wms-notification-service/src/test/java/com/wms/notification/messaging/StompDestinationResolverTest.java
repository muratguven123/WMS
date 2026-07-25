package com.wms.notification.messaging;

import com.wms.events.DomainEvent;
import com.wms.events.EventType;
import com.wms.events.StompDestinations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StompDestinationResolver} birim testleri.
 *
 * <p>Her {@link EventType} dalı için üretilen STOMP hedeflerini, tenant/lokasyon/kullanıcı
 * kombinasyonlarını ve payload'dan kullanıcı çözümlemesini doğrular. Saf birim testi — Docker gerektirmez.
 */
class StompDestinationResolverTest {

    private final StompDestinationResolver resolver = new StompDestinationResolver();

    private static DomainEvent event(EventType type, Long companyId, Long locationId, Map<String, Object> payload) {
        return DomainEvent.of(type, companyId, locationId, payload);
    }

    @Test
    @DisplayName("companyId null ise hiçbir hedef üretilmez")
    void nullCompany_yieldsNoDestinations() {
        DomainEvent e = event(EventType.STOCK_CHANGED, null, 5L, null);
        assertThat(resolver.resolve(e)).isEmpty();
    }

    @Test
    @DisplayName("STOCK_CHANGED — location varsa stock topic'i üretilir")
    void stockChanged_withLocation() {
        DomainEvent e = event(EventType.STOCK_CHANGED, 1L, 2L, null);
        assertThat(resolver.resolve(e)).containsExactly(StompDestinations.stock(1L, 2L));
    }

    @Test
    @DisplayName("STOCK_CHANGED — location null ise hedef yok")
    void stockChanged_withoutLocation() {
        DomainEvent e = event(EventType.STOCK_CHANGED, 1L, null, null);
        assertThat(resolver.resolve(e)).isEmpty();
    }

    @Test
    @DisplayName("LOCATION_UPDATED — location varsa locations topic'i üretilir")
    void locationUpdated_withLocation() {
        DomainEvent e = event(EventType.LOCATION_UPDATED, 3L, 7L, null);
        assertThat(resolver.resolve(e)).containsExactly(StompDestinations.locations(3L, 7L));
    }

    @Test
    @DisplayName("APPROVAL_CREATED — company approvals ve requestedBy kullanıcı kuyruğu")
    void approvalCreated_withRequestedBy() {
        DomainEvent e = event(EventType.APPROVAL_CREATED, 9L, null, Map.of("requestedByUserId", "42"));
        assertThat(resolver.resolve(e))
                .containsExactly(StompDestinations.approvals(9L), StompDestinations.userApprovals(42L));
    }

    @Test
    @DisplayName("APPROVAL_RESOLVED — payload'da kullanıcı yoksa sadece company approvals")
    void approvalResolved_withoutRequestedBy() {
        DomainEvent e = event(EventType.APPROVAL_RESOLVED, 9L, null, null);
        assertThat(resolver.resolve(e)).containsExactly(StompDestinations.approvals(9L));
    }

    @Test
    @DisplayName("INTEGRATION_LOG_UPDATED — integrations topic'i")
    void integrationLogUpdated() {
        DomainEvent e = event(EventType.INTEGRATION_LOG_UPDATED, 4L, null, null);
        assertThat(resolver.resolve(e)).containsExactly(StompDestinations.integrations(4L));
    }

    @Test
    @DisplayName("TASK_ASSIGNED — location tasks ve atanan kullanıcı kuyruğu")
    void taskAssigned_locationAndUser() {
        DomainEvent e = event(EventType.TASK_ASSIGNED, 1L, 2L, Map.of("assignedUserId", "77"));
        assertThat(resolver.resolve(e))
                .containsExactly(StompDestinations.tasks(1L, 2L), StompDestinations.userTasks(77L));
    }

    @Test
    @DisplayName("TASK_PROGRESS — location tasks ve operatör kuyruğu")
    void taskProgress_locationAndOperator() {
        DomainEvent e = event(EventType.TASK_PROGRESS, 1L, 2L, Map.of("operatorUserId", "88"));
        assertThat(resolver.resolve(e))
                .containsExactly(StompDestinations.tasks(1L, 2L), StompDestinations.userTasks(88L));
    }

    @Test
    @DisplayName("TASK_COMPLETED — sadece location tasks (kullanıcı kuyruğu yok)")
    void taskCompleted_locationOnly() {
        DomainEvent e = event(EventType.TASK_COMPLETED, 1L, 2L, Map.of("assignedUserId", "77"));
        assertThat(resolver.resolve(e)).containsExactly(StompDestinations.tasks(1L, 2L));
    }

    @Test
    @DisplayName("OPERATOR_STATUS_UPDATED — location varsa operators topic'i")
    void operatorStatusUpdated_withLocation() {
        DomainEvent e = event(EventType.OPERATOR_STATUS_UPDATED, 1L, 2L, null);
        assertThat(resolver.resolve(e)).containsExactly(StompDestinations.operators(1L, 2L));
    }

    @Test
    @DisplayName("COMPANY_CHANGED — org companies topic'i")
    void companyChanged_orgTopic() {
        DomainEvent e = event(EventType.COMPANY_CHANGED, 1L, null, null);
        assertThat(resolver.resolve(e)).containsExactly(StompDestinations.orgCompanies());
    }

    @Test
    @DisplayName("LOCATION_PROVISIONED — resolver tarafından ele alınmaz (default dal)")
    void locationProvisioned_notRouted() {
        DomainEvent e = event(EventType.LOCATION_PROVISIONED, 1L, 2L, null);
        assertThat(resolver.resolve(e)).isEmpty();
    }

    @Test
    @DisplayName("Payload kullanıcı anahtarı yoksa NPE atmadan sadece company hedefi döner")
    void missingPayloadKey_handledGracefully() {
        DomainEvent e = event(EventType.TASK_ASSIGNED, 1L, 2L, Map.of("someOtherKey", "x"));
        assertThat(resolver.resolve(e)).containsExactly(StompDestinations.tasks(1L, 2L));
    }
}

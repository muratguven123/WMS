package com.wms.integration.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Desteklenen ERP / muhasebe sistemi tanımı.
 *
 * <p>Sisteme yeni bir ERP eklendiğinde bu tabloya bir kayıt eklenir;
 * kod değişikliğine gerek kalmaz (Open/Closed). {@code code} alanı,
 * {@link com.wms.integration.adapter.ErpAdapterFactory}'nin
 * Spring bean adını türetmek için kullandığı referans değerdir
 * (örn: {@code "SAP"} → bean {@code "sapAdapter"}).
 *
 * <p>Örnek kayıtlar: SAP, ORACLE, LOGO, MIKRO, MOCK
 */
@Entity
@Table(name = "integration_systems",
        indexes = {
                @Index(name = "idx_integration_system_code", columnList = "code")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationSystem extends BaseEntity {

    /**
     * Benzersiz ERP kodu — Spring adapter bean adıyla örtüşmeli.
     * Örn: SAP, ORACLE, LOGO, MIKRO, MOCK
     */
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    /** Okunabilir ERP adı. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * {@link LocationIntegrationConfig} ilişkisi — cascade/fetch lazy;
     * entity grafiği burada gerekli değil, referans bütünlüğü için tutulur.
     */
    @OneToMany(mappedBy = "integrationSystem", fetch = FetchType.LAZY)
    @Builder.Default
    private java.util.List<LocationIntegrationConfig> locationConfigs = new java.util.ArrayList<>();
}

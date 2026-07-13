package com.wms.integration.entity;

import com.wms.integration.entity.enums.ConnectionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Depo (lokasyon) bazında ERP bağlantı konfigürasyonu.
 *
 * <h3>connectionParams (JSONB)</h3>
 * Bağlantı tiplerine göre beklenen alanlar:
 * <pre>
 * REST / SOAP:
 *   { "baseUrl": "https://...", "apiKey": "...", "username": "...", "password": "..." }
 *
 * SFTP:
 *   { "host": "sftp.host", "port": 22, "user": "wms", "password": "...",
 *     "remoteDir": "/import", "knownHostsFile": "/etc/ssh/known_hosts" }
 *
 * DB:
 *   { "jdbcUrl": "jdbc:postgresql://...", "dbUser": "...", "dbPassword": "...",
 *     "schema": "erp_schema" }
 * </pre>
 *
 * <p><b>Güvenlik notu:</b> Üretim ortamında {@code connectionParams} içindeki
 * hassas alanlar (apiKey, password) Vault / AWS Secrets Manager referansı
 * olarak saklanmalı; düz metin olarak yazılmamalıdır.
 *
 * <p>Her lokasyon için en fazla 1 aktif config olması zorunluluğu partial unique
 * index ile sağlanır: {@code idx_loc_int_cfg_active_unique}.
 */
@Entity
@Table(name = "location_integration_configs",
        indexes = {
                @Index(name = "idx_loc_int_cfg_location_id",  columnList = "location_id"),
                @Index(name = "idx_loc_int_cfg_system_id",    columnList = "integration_system_id"),
                @Index(name = "idx_loc_int_cfg_is_active",    columnList = "is_active")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationIntegrationConfig extends BaseEntity {

    /**
     * wms-core-service Location.id ile eşleşir.
     * FK tanımlanmaz — servisler arası bağımlılık önlenir.
     */
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_system_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_loc_int_cfg_system"))
    private IntegrationSystem integrationSystem;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", nullable = false, length = 10)
    private ConnectionType connectionType;

    /**
     * Bağlantı parametreleri — PostgreSQL JSONB.
     *
     * <p>Hibernate 6 {@code @JdbcTypeCode(SqlTypes.JSON)} ile JSONB kolonuyla
     * eşleştirilir. {@code Map<String, Object>} kullanımı tip güvenliğini
     * yeterince sağlar; daha katı şema için ayrı bir VO sınıfı tercih edilebilir.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "connection_params", columnDefinition = "jsonb")
    private Map<String, Object> connectionParams;
}

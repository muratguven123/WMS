package com.wms.integration.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * wms-integration-service için ortak base entity.
 *
 * <p>BIGINT PK, soft-delete flag ve audit timestamp'leri merkezi olarak yönetilir.
 * wms-core-service'deki BaseEntity ile aynı sözleşmeyi izler; ancak
 * servisler arası sınır (microservice boundary) korunduğu için bağımlılık
 * oluşturulmaz — her servis kendi BaseEntity'sini taşır.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

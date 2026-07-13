package com.wms.inbound.entity;

import com.wms.inbound.entity.enums.InboundOrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "inbound_orders",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_inbound_order_number", columnNames = {"order_number"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "order_number", nullable = false, length = 100)
    private String orderNumber;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Mal kabulün yapıldığı depo (Faz 1 Location) — putaway için kullanılır. */
    @Column(name = "warehouse_location_id")
    private Long warehouseLocationId;

    @Column(name = "supplier_name", nullable = false, length = 255)
    private String supplierName;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private InboundOrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "inboundOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InboundOrderItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

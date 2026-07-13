package com.wms.outbound.entity;

import com.wms.outbound.entity.enums.ShipmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "shipments",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_shipment_number", columnNames = {"shipment_number"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "shipment_number", nullable = false, unique = true, length = 100)
    private String shipmentNumber;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "warehouse_location_id")
    private Long warehouseLocationId;

    @Column(name = "carrier_code", length = 100)
    private String carrierCode;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ShipmentStatus status;

    @Column(name = "total_boxes", nullable = false)
    @Builder.Default
    private int totalBoxes = 0;

    @Column(name = "total_weight", precision = 10, scale = 4)
    private BigDecimal totalWeight;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @OneToMany(mappedBy = "shipment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShipmentItem> items = new ArrayList<>();
}

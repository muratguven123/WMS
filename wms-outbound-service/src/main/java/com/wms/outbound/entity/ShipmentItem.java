package com.wms.outbound.entity;

import com.wms.outbound.entity.enums.ShipmentItemStatus;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "shipment_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "shipment_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_shipment_item_shipment")
    )
    private Shipment shipment;

    @Column(name = "outbound_order_id", nullable = false)
    private Long outboundOrderId;

    @Column(name = "box_sscc_number", nullable = false, length = 100)
    private String boxSsccNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ShipmentItemStatus status;
}

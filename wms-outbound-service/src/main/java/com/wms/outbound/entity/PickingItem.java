package com.wms.outbound.entity;

import com.wms.outbound.entity.enums.PickingItemStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "picking_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "picking_list_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_item_picking_list")
    )
    private PickingList pickingList;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "outbound_order_item_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_item_picking_outbound")
    )
    private OutboundOrderItem outboundOrderItem;

    @Column(name = "source_location_id", nullable = false)
    private Long sourceLocationId;

    @Column(name = "address_code", nullable = false, length = 100)
    private String addressCode;

    @Column(name = "quantity_to_pick", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantityToPick;

    @Column(name = "picked_quantity", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal pickedQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private PickingItemStatus status;
}

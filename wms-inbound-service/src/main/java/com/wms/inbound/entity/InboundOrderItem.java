package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "inbound_order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "inbound_order_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_item_inbound_order")
    )
    private InboundOrder inboundOrder;

    @Column(name = "product_code", nullable = false, length = 100)
    private String productCode;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "received_quantity", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    @Column(name = "uom", nullable = false, length = 50)
    private String uom;

    @Column(name = "unit_volume", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitVolume;

    @Column(name = "unit_weight", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitWeight;
}

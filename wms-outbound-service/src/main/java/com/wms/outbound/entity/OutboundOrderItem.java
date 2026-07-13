package com.wms.outbound.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "outbound_order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboundOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "outbound_order_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_item_outbound_order")
    )
    private OutboundOrder outboundOrder;

    @Column(name = "product_code", nullable = false, length = 100)
    private String productCode;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "allocated_quantity", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal allocatedQuantity = BigDecimal.ZERO;

    @Column(name = "picked_quantity", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal pickedQuantity = BigDecimal.ZERO;
}

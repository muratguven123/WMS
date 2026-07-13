package com.wms.inbound.entity;

import com.wms.inbound.entity.enums.ReceiptItemQcStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "receipt_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "receipt_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_item_receipt")
    )
    private Receipt receipt;

    @Column(name = "product_code", nullable = false, length = 100)
    private String productCode;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "qc_status", nullable = false, length = 50)
    private ReceiptItemQcStatus qcStatus;
}

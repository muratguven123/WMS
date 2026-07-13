package com.wms.inbound.entity;

import com.wms.inbound.entity.enums.ReceiptStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "receipts",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_receipt_number", columnNames = {"receipt_number"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "inbound_order_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_receipt_inbound_order")
    )
    private InboundOrder inboundOrder;

    @Column(name = "receipt_number", nullable = false, length = 100)
    private String receiptNumber;

    @Column(name = "received_by_user_id", nullable = false)
    private Long receivedByUserId;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ReceiptStatus status;

    @OneToMany(mappedBy = "receipt", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReceiptItem> items = new ArrayList<>();
}

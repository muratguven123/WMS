package com.wms.finance.entity;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(
        name = "customer_permitted_currencies",
        schema = "finance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cpc_customer_currency",
                columnNames = {"customer_id", "currency_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerPermittedCurrency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cpc_customer"))
    private FinanceCustomer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cpc_currency"))
    private Currency currency;
}

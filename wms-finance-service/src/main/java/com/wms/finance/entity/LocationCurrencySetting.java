package com.wms.finance.entity;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "location_currency_settings", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationCurrencySetting {

    @Id
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "local_currency_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_lcs_local_currency"))
    private Currency localCurrency;
}

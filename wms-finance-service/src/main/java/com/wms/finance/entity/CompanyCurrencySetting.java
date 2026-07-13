package com.wms.finance.entity;

import jakarta.persistence.*;
import lombok.*;


/**
 * wms-core {@code Company} kaydına karşılık gelen para birimi ayarı.
 */
@Entity
@Table(name = "company_currency_settings", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyCurrencySetting {

    @Id
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_currency_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ccs_base_currency"))
    private Currency baseCurrency;
}

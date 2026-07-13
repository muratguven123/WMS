package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "roles",
       uniqueConstraints = @UniqueConstraint(name = "uk_role_name", columnNames = "name"))
@SQLDelete(sql = "UPDATE roles SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Yetki listesi JSONB olarak saklanır.
     * Örn: ["STOCK_VIEW", "STOCK_EDIT", "ORDER_CREATE"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions", columnDefinition = "jsonb")
    private List<String> permissions;

    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    @Builder.Default
    private List<UserAccess> userAccesses = new ArrayList<>();
}

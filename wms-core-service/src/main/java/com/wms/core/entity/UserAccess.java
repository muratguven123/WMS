package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Kullanıcının hangi şirkette, hangi lokasyonda, hangi rolle yetkili olduğunu tanımlar.
 *
 * location null ise → kullanıcı o şirketteki TÜM lokasyonlarda yetkilidir.
 * Soft delete uygulanmaz — erişim geri alınacaksa satır silinir.
 */
@Entity
@Table(name = "user_accesses",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_user_access",
           columnNames = {"user_id", "company_id", "location_id", "role_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_user_access_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_user_access_company"))
    private Company company;

    /**
     * Nullable — null ise kullanıcı şirketteki tüm lokasyonlara erişebilir.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id",
                foreignKey = @ForeignKey(name = "fk_user_access_location"))
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_user_access_role"))
    private Role role;
}

package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_user_username", columnNames = "username"),
           @UniqueConstraint(name = "uk_user_email", columnNames = "email"),
           @UniqueConstraint(name = "uk_user_keycloak_id", columnNames = "keycloak_user_id")
       })
@SQLDelete(sql = "UPDATE users SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Keycloak'taki kullanıcı kimliği (JWT `sub` claim'i).
     * Gelen token'daki sub değeri bu alanla eşleştirilerek yerel kullanıcı bulunur.
     * Keycloak entegrasyonu öncesi oluşturulan kullanıcılar için nullable.
     */
    @Column(name = "keycloak_user_id", length = 36)
    private String keycloakUserId;

    /**
     * Kullanıcının tercih ettiği IANA timezone — örn: Europe/Istanbul, America/New_York.
     * null ise kullanıcının erişim yetkisi olan Location.timezone'u devreye girer.
     */
    @Column(name = "preferred_timezone", length = 50)
    private String preferredTimezone;

    @Column(name = "preferred_language", length = 5)
    private String preferredLanguage;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Builder.Default
    private List<UserAccess> userAccesses = new ArrayList<>();
}

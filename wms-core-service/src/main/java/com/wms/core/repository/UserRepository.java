package com.wms.core.repository;

import com.wms.core.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /** Keycloak JWT `sub` claim'i ile yerel kullanıcı eşleştirmesi. */
    Optional<User> findByKeycloakUserId(String keycloakUserId);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}

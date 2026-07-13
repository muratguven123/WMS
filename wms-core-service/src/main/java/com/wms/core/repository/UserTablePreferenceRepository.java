package com.wms.core.repository;

import com.wms.core.entity.UserTablePreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTablePreferenceRepository extends JpaRepository<UserTablePreference, Long> {

    Optional<UserTablePreference> findByUserIdAndScreenId(Long userId, Long screenId);

    void deleteByUserIdAndScreenId(Long userId, Long screenId);
}

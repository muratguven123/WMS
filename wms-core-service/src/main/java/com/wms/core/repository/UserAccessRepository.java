package com.wms.core.repository;

import com.wms.core.entity.UserAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserAccessRepository extends JpaRepository<UserAccess, Long> {

    List<UserAccess> findByUserId(Long userId);

    List<UserAccess> findByUserIdAndCompanyId(Long userId, Long companyId);

    @org.springframework.data.jpa.repository.Query("""
            SELECT ua FROM UserAccess ua
            JOIN FETCH ua.company
            WHERE ua.user.id = :userId
            """)
    List<UserAccess> findByUserIdWithCompany(@org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Query("""
            SELECT ua FROM UserAccess ua
            LEFT JOIN FETCH ua.location
            WHERE ua.user.id = :userId AND ua.company.id = :companyId
            """)
    List<UserAccess> findByUserIdAndCompanyIdWithLocation(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("companyId") Long companyId);

    /**
     * Kullanıcının belirli bir şirket+lokasyon çiftine erişimi var mı kontrol eder.
     * location_id NULL ise → şirketteki tüm lokasyonlara erişim var demektir.
     */
    @Query("""
        SELECT CASE WHEN COUNT(ua) > 0 THEN true ELSE false END
        FROM UserAccess ua
        WHERE ua.user.id = :userId
          AND ua.company.id = :companyId
          AND (ua.location IS NULL OR ua.location.id = :locationId)
    """)
    boolean hasAccess(@Param("userId") Long userId,
                      @Param("companyId") Long companyId,
                      @Param("locationId") Long locationId);
}

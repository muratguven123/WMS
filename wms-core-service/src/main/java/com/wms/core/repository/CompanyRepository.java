package com.wms.core.repository;

import com.wms.core.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findByOrganizationId(Long organizationId);

    Optional<Company> findByTaxNumber(String taxNumber);

    boolean existsByTaxNumber(String taxNumber);

    // ------------------------------------------------------------------
    // Admin CRUD — @SQLRestriction("is_active = true") pasif kayıtları
    // gizlediği için native sorgular kullanılır.
    // ------------------------------------------------------------------

    /** Admin listesi: pasif firmalar dahil tüm kayıtlar + org adı + aktif depo sayısı. */
    @Query(value = """
            SELECT c.id,
                   c.organization_id,
                   o.name,
                   c.name,
                   c.tax_number,
                   c.tax_office,
                   c.is_active,
                   (SELECT COUNT(*) FROM locations l
                    WHERE l.company_id = c.id AND l.is_active = true)
            FROM companies c
            JOIN organizations o ON o.id = c.organization_id
            ORDER BY c.name
            """, nativeQuery = true)
    List<Object[]> findAllAdminRows();

    /** Pasif kayıtlar dahil id ile arama (reaktivasyon / usage). */
    @Query(value = "SELECT * FROM companies WHERE id = :id", nativeQuery = true)
    Optional<Company> findByIdIncludingInactive(@Param("id") Long id);

    /**
     * Pasif kayıtlar dahil tax_number tekillik kontrolü.
     * DB'deki uk_company_tax_number kısıtı pasif kayıtları da kapsar.
     */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM companies WHERE tax_number = :taxNumber)", nativeQuery = true)
    boolean existsByTaxNumberIncludingInactive(@Param("taxNumber") String taxNumber);

    /** Güncellemede kendisi hariç tax_number tekillik kontrolü. */
    @Query(value = """
            SELECT EXISTS(
                SELECT 1 FROM companies
                WHERE tax_number = :taxNumber AND id <> :excludeId
            )
            """, nativeQuery = true)
    boolean existsByTaxNumberIncludingInactiveExcludingId(
            @Param("taxNumber") String taxNumber,
            @Param("excludeId") Long excludeId);

    /** Pasif firmayı yeniden aktifleştirir (soft-delete geri alma). */
    @Modifying
    @Query(value = "UPDATE companies SET is_active = true, updated_at = now() WHERE id = :id", nativeQuery = true)
    int reactivate(@Param("id") Long id);
}

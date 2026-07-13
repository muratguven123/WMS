package com.wms.localization.repository.address;

import com.wms.localization.domain.address.Address;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    Page<Address> findByCountryId(Long countryId, Pageable pageable);

    Page<Address> findByCountryIdAndCity(Long countryId, String city, Pageable pageable);

    Page<Address> findByCountryIdAndState(Long countryId, String state, Pageable pageable);

    List<Address> findByZipCode(String zipCode);

    /**
     * JSONB içindeki belirli bir key-value çiftine göre adres arama.
     *
     * <p>Örnek çağrı: {@code searchByJsonField("district", "Kadıköy")}</p>
     *
     * <p>PostgreSQL JSONB operatörü {@code @>} kullanır ve GIN index'ten yararlanır.</p>
     */
    @Query(value = """
            SELECT * FROM localization.address
            WHERE address_details @> jsonb_build_object(:fieldKey, :fieldValue::text)
            """, nativeQuery = true)
    List<Address> searchByJsonField(
            @Param("fieldKey") String fieldKey,
            @Param("fieldValue") String fieldValue
    );

    /**
     * formattedAddress üzerinde case-insensitive partial arama.
     */
    @Query("""
            SELECT a FROM Address a
            WHERE LOWER(a.formattedAddress) LIKE LOWER(CONCAT('%', :query, '%'))
            AND a.countryId = :countryId
            """)
    Page<Address> searchByFormattedAddress(
            @Param("countryId") Long countryId,
            @Param("query") String query,
            Pageable pageable
    );
}

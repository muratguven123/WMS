package com.wms.localization.repository.address;

import com.wms.localization.domain.address.AddressTemplateField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AddressTemplateFieldRepository extends JpaRepository<AddressTemplateField, Long> {

    Optional<AddressTemplateField> findByFieldKey(String fieldKey);

    boolean existsByFieldKey(String fieldKey);

    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(cat) > 0 FROM CountryAddressTemplate cat
            WHERE cat.addressTemplateField.id = :fieldId
            """)
    boolean existsInAnyCountryTemplate(@org.springframework.data.repository.query.Param("fieldId") Long fieldId);
}

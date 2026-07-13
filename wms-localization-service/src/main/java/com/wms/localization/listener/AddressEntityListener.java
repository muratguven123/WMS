package com.wms.localization.listener;

import com.wms.localization.domain.address.Address;
import com.wms.localization.service.address.AddressFormatterService;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link Address} entity'si persist/update edilmeden önce
 * {@code formattedAddress} alanını otomatik doldurur.
 *
 * <h3>Spring Bean Erişimi Sorunu</h3>
 * <p>JPA entity listener'ları Hibernate tarafından instantiate edildiğinden
 * normal {@code @Autowired} çalışmaz. Çözüm olarak Spring'in
 * {@link ApplicationContext} köprüsü olan {@link SpringContextHolder} kullanılır.
 * Bu pattern, Hibernate'in Spring entegrasyonunda yaygın kabul gören yaklaşımdır.</p>
 *
 * <h3>Entity'ye Bağlama</h3>
 * <pre>{@code
 * @EntityListeners(AddressEntityListener.class)
 * public class Address { ... }
 * }</pre>
 */
@Slf4j
@Component
public class AddressEntityListener {

    @PrePersist
    public void onPrePersist(Address address) {
        fillFormattedAddress(address);
    }

    @PreUpdate
    public void onPreUpdate(Address address) {
        fillFormattedAddress(address);
    }

    // -------------------------------------------------------------------------

    private void fillFormattedAddress(Address address) {
        try {
            AddressFormatterService formatterService =
                    SpringContextHolder.getBean(AddressFormatterService.class);

            String formatted = formatterService.generateFormattedAddress(
                    address.getCountryId(),
                    address.getAddressDetails(),
                    address.getCity(),
                    address.getState(),
                    address.getZipCode()
            );
            address.setFormattedAddress(formatted);
            log.debug("formattedAddress set for address id={}: '{}'", address.getId(), formatted);

        } catch (Exception e) {
            // formattedAddress doldurulamaması persist işlemini engellememeli;
            // hata loglanır ve alan boş bırakılır.
            log.error("Failed to generate formattedAddress for address id={}: {}",
                    address.getId(), e.getMessage(), e);
        }
    }
}

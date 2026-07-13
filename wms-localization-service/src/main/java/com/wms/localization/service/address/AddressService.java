package com.wms.localization.service.address;

import com.wms.localization.domain.address.Address;
import com.wms.localization.dto.address.AddressDto;
import com.wms.localization.dto.address.AddressResponse;
import com.wms.localization.dto.address.CountryAddressTemplateDto;
import com.wms.localization.exception.address.AddressNotFoundException;
import com.wms.localization.repository.address.AddressRepository;
import com.wms.localization.repository.address.CountryAddressTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * Adres oluşturma ve güncelleme servisi.
 *
 * <p>Her write işleminde {@link AddressValidationService} ile şablon doğrulaması yapılır;
 * {@link com.wms.localization.listener.AddressEntityListener} persist öncesi
 * {@code formattedAddress} alanını doldurur.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AddressService {

    private final AddressRepository addressRepository;
    private final AddressValidationService validationService;
    private final CountryAddressTemplateRepository templateRepository;

    /**
     * Bir ülke için tanımlı dinamik adres şablonunu, gösterim sırasına göre döner.
     *
     * <p>UI bu listeyi kullanarak city altındaki alanları tamamen dinamik render eder;
     * hiçbir ülke adı/kodu üzerinden dallanma yapılmaz. Ülke için şablon tanımlı
     * değilse boş liste döner — bu, "bu ülke için ek adres alanı yok, sadece sabit
     * alanları göster" anlamına gelir ve hata sayılmaz.</p>
     */
    @Transactional(readOnly = true)
    public List<CountryAddressTemplateDto> getTemplateByCountry(Long countryId) {
        return templateRepository.findByCountryIdOrderBySequenceAsc(countryId).stream()
                .map(CountryAddressTemplateDto::from)
                .toList();
    }

    public AddressResponse create(AddressDto dto) {
        validationService.validateAddress(dto);
        Address saved = addressRepository.save(toEntity(dto));
        return toResponse(saved);
    }

    public AddressResponse update(Long id, AddressDto dto) {
        Address existing = addressRepository.findById(id)
                .orElseThrow(() -> new AddressNotFoundException(id));

        validationService.validateAddress(dto);
        applyDto(existing, dto);
        return toResponse(addressRepository.save(existing));
    }

    @Transactional(readOnly = true)
    public AddressResponse getById(Long id) {
        return addressRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new AddressNotFoundException(id));
    }

    /**
     * Kayıtlı adresleri sayfalı listeler. {@code countryId} verilirse yalnızca o ülkeye ait
     * adresler döner.
     */
    @Transactional(readOnly = true)
    public Page<AddressResponse> list(Long countryId, Pageable pageable) {
        Page<Address> page = countryId != null
                ? addressRepository.findByCountryId(countryId, pageable)
                : addressRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    private Address toEntity(AddressDto dto) {
        return Address.builder()
                .countryId(dto.getCountryId())
                .city(dto.getCity())
                .state(dto.getState())
                .zipCode(dto.getZipCode())
                .addressDetails(dto.getAddressDetails())
                .build();
    }

    private void applyDto(Address entity, AddressDto dto) {
        entity.setCountryId(dto.getCountryId());
        entity.setCity(dto.getCity());
        entity.setState(dto.getState());
        entity.setZipCode(dto.getZipCode());
        entity.setAddressDetails(dto.getAddressDetails());
    }

    private AddressResponse toResponse(Address entity) {
        return new AddressResponse(
                entity.getId(),
                entity.getCountryId(),
                entity.getCity(),
                entity.getState(),
                entity.getZipCode(),
                entity.getAddressDetails(),
                entity.getFormattedAddress()
        );
    }
}

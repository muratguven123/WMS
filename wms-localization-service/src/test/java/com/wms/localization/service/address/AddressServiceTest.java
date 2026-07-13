package com.wms.localization.service.address;

import com.wms.localization.domain.address.Address;
import com.wms.localization.domain.address.AddressTemplateField;
import com.wms.localization.domain.address.CountryAddressTemplate;
import com.wms.localization.dto.address.AddressDto;
import com.wms.localization.dto.address.CountryAddressTemplateDto;
import com.wms.localization.exception.address.AddressValidationException;
import com.wms.localization.repository.address.AddressRepository;
import com.wms.localization.repository.address.CountryAddressTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressService")
class AddressServiceTest {

    @Mock private AddressRepository addressRepository;
    @Mock private AddressValidationService validationService;
    @Mock private CountryAddressTemplateRepository templateRepository;

    @InjectMocks
    private AddressService addressService;

    private static final Long COUNTRY_TR =
            1L;

    @Test
    @DisplayName("create önce doğrulama yapar sonra kaydeder")
    void create_validatesThenSaves() {
        AddressDto dto = AddressDto.builder()
                .countryId(COUNTRY_TR)
                .city("İstanbul")
                .state("İstanbul")
                .zipCode("34710")
                .addressDetails(Map.of("district", "Kadıköy", "neighborhood", "Moda"))
                .build();

        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> {
            Address entity = inv.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        addressService.create(dto);

        verify(validationService).validateAddress(dto);
        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().getCity()).isEqualTo("İstanbul");
        assertThat(captor.getValue().getAddressDetails()).containsEntry("district", "Kadıköy");
    }

    @Test
    @DisplayName("doğrulama başarısızsa kayıt yapılmaz")
    void create_skipsSaveWhenValidationFails() {
        AddressDto dto = AddressDto.builder()
                .countryId(COUNTRY_TR)
                .build();

        doThrow(AddressValidationException.of("validation.district.required"))
                .when(validationService).validateAddress(dto);

        assertThatThrownBy(() -> addressService.create(dto))
                .isInstanceOf(AddressValidationException.class);

        verify(addressRepository, never()).save(any());
    }

    @Test
    @DisplayName("getTemplateByCountry şablonu olan ülke için sıralı DTO listesi döner")
    void getTemplateByCountry_returnsOrderedDtos() {
        AddressTemplateField districtField = AddressTemplateField.builder()
                .fieldKey("district")
                .fieldLabelKey("fields.district")
                .fieldType(AddressTemplateField.FieldType.TEXT)
                .masterDataSource(AddressTemplateField.MasterDataSource.NONE)
                .build();
        CountryAddressTemplate districtTemplate = CountryAddressTemplate.builder()
                .countryId(COUNTRY_TR)
                .addressTemplateField(districtField)
                .mandatory(true)
                .sequence(1)
                .errorMessageKey("validation.district.required")
                .build();

        when(templateRepository.findByCountryIdOrderBySequenceAsc(COUNTRY_TR))
                .thenReturn(List.of(districtTemplate));

        List<CountryAddressTemplateDto> result = addressService.getTemplateByCountry(COUNTRY_TR);

        assertThat(result).hasSize(1);
        CountryAddressTemplateDto dto = result.get(0);
        assertThat(dto.getFieldKey()).isEqualTo("district");
        assertThat(dto.isMandatory()).isTrue();
        assertThat(dto.getFieldType()).isEqualTo("TEXT");
        assertThat(dto.getMasterDataSource()).isEqualTo("NONE");
        assertThat(dto.getParentFieldKey()).isNull();
    }

    @Test
    @DisplayName("getTemplateByCountry şablonu olmayan ülke için boş liste döner")
    void getTemplateByCountry_returnsEmptyListWhenNoTemplate() {
        when(templateRepository.findByCountryIdOrderBySequenceAsc(eq(999L)))
                .thenReturn(List.of());

        List<CountryAddressTemplateDto> result = addressService.getTemplateByCountry(999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("list tüm adresleri sayfalı döner")
    void list_returnsAllAddresses() {
        Address entity = Address.builder()
                .id(5L)
                .countryId(COUNTRY_TR)
                .city("Berlin")
                .zipCode("10115")
                .formattedAddress("Berlin 10115")
                .build();

        when(addressRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1));

        Page<com.wms.localization.dto.address.AddressResponse> page =
                addressService.list(null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).city()).isEqualTo("Berlin");
    }

    @Test
    @DisplayName("list countryId verilirse ülkeye göre filtreler")
    void list_filtersByCountry() {
        when(addressRepository.findByCountryId(eq(COUNTRY_TR), any(Pageable.class)))
                .thenReturn(Page.empty());

        addressService.list(COUNTRY_TR, PageRequest.of(0, 20));

        verify(addressRepository).findByCountryId(eq(COUNTRY_TR), any(Pageable.class));
    }
}

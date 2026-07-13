package com.wms.localization.service.address;

import com.wms.localization.domain.address.AddressTemplateField;
import com.wms.localization.domain.address.CountryAddressTemplate;
import com.wms.localization.dto.address.AddressDto;
import com.wms.localization.exception.address.AddressValidationException;
import com.wms.localization.repository.address.CountryAddressTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressValidationService")
class AddressValidationServiceTest {

    @Mock
    private CountryAddressTemplateRepository templateRepository;

    @InjectMocks
    private AddressValidationService validationService;

    private Long countryId;

    @BeforeEach
    void setUp() {
        countryId = 1L;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test yardımcıları
    // ─────────────────────────────────────────────────────────────────────────

    private AddressTemplateField field(String key) {
        return AddressTemplateField.builder()
                .id(1L)
                .fieldKey(key)
                .fieldLabelKey("fields." + key)
                .build();
    }

    private CountryAddressTemplate mandatoryTemplate(String fieldKey, String regex, String errorKey) {
        return CountryAddressTemplate.builder()
                .id(1L)
                .countryId(countryId)
                .addressTemplateField(field(fieldKey))
                .mandatory(true)
                .sequence(1)
                .validationRegex(regex)
                .errorMessageKey(errorKey)
                .build();
    }

    private CountryAddressTemplate optionalTemplate(String fieldKey, String regex, String errorKey) {
        return CountryAddressTemplate.builder()
                .id(1L)
                .countryId(countryId)
                .addressTemplateField(field(fieldKey))
                .mandatory(false)
                .sequence(2)
                .validationRegex(regex)
                .errorMessageKey(errorKey)
                .build();
    }

    private AddressDto dto(Map<String, Object> details) {
        return AddressDto.builder()
                .countryId(countryId)
                .addressDetails(details)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test senaryoları
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Şablon bulunamadığında")
    class NoTemplates {

        @Test
        @DisplayName("exception fırlatmadan geçmeli")
        void shouldPassWhenNoTemplatesExist() {
            given(templateRepository.findByCountryIdOrderBySequenceAsc(countryId))
                    .willReturn(List.of());

            assertThatNoException()
                    .isThrownBy(() -> validationService.validateAddress(dto(Map.of())));
        }
    }

    @Nested
    @DisplayName("Zorunlu alan kontrolleri")
    class MandatoryFieldValidation {

        @Test
        @DisplayName("Zorunlu alan eksikse AddressValidationException fırlatmalı")
        void shouldThrowWhenMandatoryFieldMissing() {
            CountryAddressTemplate template = mandatoryTemplate(
                    "district", null, "validation.district.required");

            given(templateRepository.findByCountryIdOrderBySequenceAsc(countryId))
                    .willReturn(List.of(template));

            AddressValidationException ex = catchThrowableOfType(
                    () -> validationService.validateAddress(dto(Map.of())),
                    AddressValidationException.class
            );

            assertThat(ex).isNotNull();
            assertThat(ex.getErrorMessageKeys())
                    .containsExactly("validation.district.required");
        }

        @Test
        @DisplayName("Zorunlu alan null string içeriyorsa AddressValidationException fırlatmalı")
        void shouldThrowWhenMandatoryFieldIsBlank() {
            CountryAddressTemplate template = mandatoryTemplate(
                    "district", null, "validation.district.required");

            given(templateRepository.findByCountryIdOrderBySequenceAsc(countryId))
                    .willReturn(List.of(template));

            AddressValidationException ex = catchThrowableOfType(
                    () -> validationService.validateAddress(dto(Map.of("district", "   "))),
                    AddressValidationException.class
            );

            assertThat(ex.getErrorMessageKeys())
                    .containsExactly("validation.district.required");
        }

        @Test
        @DisplayName("Zorunlu alan dolu ise geçmeli")
        void shouldPassWhenMandatoryFieldPresent() {
            CountryAddressTemplate template = mandatoryTemplate(
                    "district", null, "validation.district.required");

            given(templateRepository.findByCountryIdOrderBySequenceAsc(countryId))
                    .willReturn(List.of(template));

            assertThatNoException()
                    .isThrownBy(() -> validationService.validateAddress(
                            dto(Map.of("district", "Kadıköy"))));
        }
    }

    @Nested
    @DisplayName("Regex validasyon kontrolleri")
    class RegexValidation {

        private static final String ZIP_REGEX = "^[0-9]{5}$";
        private static final String ZIP_ERROR  = "validation.zipCode.invalid";

        @Test
        @DisplayName("Değer regex'e uymuyorsa AddressValidationException fırlatmalı")
        void shouldThrowWhenRegexMismatch() {
            CountryAddressTemplate template = mandatoryTemplate("zip_code", ZIP_REGEX, ZIP_ERROR);

            given(templateRepository.findByCountryIdOrderBySequenceAsc(countryId))
                    .willReturn(List.of(template));

            AddressValidationException ex = catchThrowableOfType(
                    () -> validationService.validateAddress(dto(Map.of("zip_code", "ABC12"))),
                    AddressValidationException.class
            );

            assertThat(ex.getErrorMessageKeys()).containsExactly(ZIP_ERROR);
        }

        @Test
        @DisplayName("Değer regex'e uyuyorsa geçmeli")
        void shouldPassWhenRegexMatches() {
            CountryAddressTemplate template = mandatoryTemplate("zip_code", ZIP_REGEX, ZIP_ERROR);

            given(templateRepository.findByCountryIdOrderBySequenceAsc(countryId))
                    .willReturn(List.of(template));

            assertThatNoException()
                    .isThrownBy(() -> validationService.validateAddress(
                            dto(Map.of("zip_code", "34710"))));
        }

        @Test
        @DisplayName("Opsiyonel alan dolu ve regex'e uyuyorsa geçmeli")
        void shouldPassWhenOptionalFieldMatchesRegex() {
            CountryAddressTemplate template = optionalTemplate("zip_code", ZIP_REGEX, ZIP_ERROR);

            given(templateRepository.findByCountryIdOrderBySequenceAsc(countryId))
                    .willReturn(List.of(template));

            assertThatNoException()
                    .isThrownBy(() -> validationService.validateAddress(
                            dto(Map.of("zip_code", "06800"))));
        }

        @Test
        @DisplayName("Opsiyonel alan boşsa regex kontrolü atlanmalı")
        void shouldSkipRegexForMissingOptionalField() {
            CountryAddressTemplate template = optionalTemplate("zip_code", ZIP_REGEX, ZIP_ERROR);

            given(templateRepository.findByCountryIdOrderBySequenceAsc(countryId))
                    .willReturn(List.of(template));

            assertThatNoException()
                    .isThrownBy(() -> validationService.validateAddress(
                            dto(Map.of("district", "Çankaya"))));
        }

        @Test
        @DisplayName("zipCode sütunu üzerinden regex doğrulaması yapılmalı")
        void shouldValidateZipCodeFromTopLevelColumn() {
            CountryAddressTemplate template = optionalTemplate("zip_code", ZIP_REGEX, ZIP_ERROR);

            given(templateRepository.findByCountryIdOrderBySequenceAsc(countryId))
                    .willReturn(List.of(template));

            AddressDto dto = AddressDto.builder()
                    .countryId(countryId)
                    .zipCode("ABC12")
                    .build();

            AddressValidationException ex = catchThrowableOfType(
                    () -> validationService.validateAddress(dto),
                    AddressValidationException.class
            );

            assertThat(ex.getErrorMessageKeys()).containsExactly(ZIP_ERROR);
        }
    }

    @Nested
    @DisplayName("Birden fazla ihlal")
    class MultipleViolations {

        @Test
        @DisplayName("Tüm ihlaller tek exception içinde raporlanmalı (fail-all)")
        void shouldCollectAllViolations() {
            CountryAddressTemplate districtTemplate = mandatoryTemplate(
                    "district", null, "validation.district.required");
            CountryAddressTemplate zipTemplate = mandatoryTemplate(
                    "zip_code", "^[0-9]{5}$", "validation.zipCode.invalid");

            given(templateRepository.findByCountryIdOrderBySequenceAsc(countryId))
                    .willReturn(List.of(districtTemplate, zipTemplate));

            // district eksik, zip_code format hatası
            AddressValidationException ex = catchThrowableOfType(
                    () -> validationService.validateAddress(
                            dto(Map.of("zip_code", "WRONG"))),
                    AddressValidationException.class
            );

            assertThat(ex.getErrorMessageKeys())
                    .containsExactlyInAnyOrder(
                            "validation.district.required",
                            "validation.zipCode.invalid"
                    );
        }
    }

    @Nested
    @DisplayName("FIXED alanlar (city/state/zip_code)")
    class FixedFieldValidation {

        @Test
        @DisplayName("state zorunlu — değer dto.state üst sütunundan okunmalı")
        void shouldReadMandatoryStateFromTopLevelColumn() {
            CountryAddressTemplate template = mandatoryTemplate("state", null, "validation.state.required");
            given(templateRepository.findByCountryIdOrderBySequenceAsc(countryId))
                    .willReturn(List.of(template));

            AddressDto missing = AddressDto.builder().countryId(countryId).build();
            assertThatThrownBy(() -> validationService.validateAddress(missing))
                    .isInstanceOf(AddressValidationException.class);

            AddressDto valid = AddressDto.builder()
                    .countryId(countryId)
                    .state("California")
                    .build();
            assertThatNoException().isThrownBy(() -> validationService.validateAddress(valid));
        }

        @Test
        @DisplayName("city zorunlu — değer dto.city üst sütunundan okunmalı")
        void shouldReadMandatoryCityFromTopLevelColumn() {
            CountryAddressTemplate template = mandatoryTemplate("city", null, "validation.city.required");
            given(templateRepository.findByCountryIdOrderBySequenceAsc(countryId))
                    .willReturn(List.of(template));

            AddressDto missing = AddressDto.builder().countryId(countryId).build();
            assertThatThrownBy(() -> validationService.validateAddress(missing))
                    .isInstanceOf(AddressValidationException.class);

            AddressDto valid = AddressDto.builder()
                    .countryId(countryId)
                    .city("İstanbul")
                    .build();
            assertThatNoException().isThrownBy(() -> validationService.validateAddress(valid));
        }

        @Test
        @DisplayName("zip_code regex — dto.zipCode üst sütunundan doğrulanmalı")
        void shouldValidateZipCodeFromTopLevelColumn() {
            CountryAddressTemplate template = optionalTemplate("zip_code", "^[0-9]{5}$", "validation.zipCode.invalid");
            given(templateRepository.findByCountryIdOrderBySequenceAsc(countryId))
                    .willReturn(List.of(template));

            AddressDto invalid = AddressDto.builder()
                    .countryId(countryId)
                    .zipCode("BAD")
                    .build();
            assertThatThrownBy(() -> validationService.validateAddress(invalid))
                    .isInstanceOf(AddressValidationException.class);

            AddressDto valid = AddressDto.builder()
                    .countryId(countryId)
                    .zipCode("34710")
                    .build();
            assertThatNoException().isThrownBy(() -> validationService.validateAddress(valid));
        }
    }

    @Nested
    @DisplayName("Fallback error key")
    class FallbackErrorKey {

        @Test
        @DisplayName("Şablonda errorMessageKey yoksa fallback key üretilmeli")
        void shouldUseFallbackKeyWhenErrorMessageKeyIsNull() {
            CountryAddressTemplate template = mandatoryTemplate("neighborhood", null, null);

            given(templateRepository.findByCountryIdOrderBySequenceAsc(countryId))
                    .willReturn(List.of(template));

            AddressValidationException ex = catchThrowableOfType(
                    () -> validationService.validateAddress(dto(Map.of())),
                    AddressValidationException.class
            );

            // Fallback: "validation.<fieldKey>.required"
            assertThat(ex.getErrorMessageKeys())
                    .containsExactly("validation.neighborhood.required");
        }
    }
}

package com.wms.localization.service.address;

import com.wms.localization.domain.address.AddressTemplateField;
import com.wms.localization.domain.address.CountryAddressTemplate;
import com.wms.localization.dto.address.*;
import com.wms.localization.exception.address.TemplateConfigException;
import com.wms.localization.integration.CoreMasterDataClient;
import com.wms.localization.repository.address.AddressTemplateFieldRepository;
import com.wms.localization.repository.address.CountryAddressTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressTemplateAdminService")
class AddressTemplateAdminServiceTest {

    @Mock private AddressTemplateFieldRepository fieldRepository;
    @Mock private CountryAddressTemplateRepository templateRepository;
    @Mock private CoreMasterDataClient coreMasterDataClient;

    @InjectMocks
    private AddressTemplateAdminService service;

    private static final Long COUNTRY_TR = 1L;
    private static final Long FIELD_STATE = 10L;
    private static final Long TEMPLATE_ID = 100L;

    private AddressTemplateField stateField() {
        return AddressTemplateField.builder()
                .fieldKey("state")
                .fieldLabelKey("fields.state")
                .fieldType(AddressTemplateField.FieldType.MASTER_SELECT)
                .masterDataSource(AddressTemplateField.MasterDataSource.STATE)
                .parentFieldKey("__country__")
                .build();
    }

    @Nested
    @DisplayName("createField")
    class CreateField {
        @Test
        void createsField() {
            when(fieldRepository.existsByFieldKey("building")).thenReturn(false);
            when(fieldRepository.save(any())).thenAnswer(inv -> {
                AddressTemplateField f = inv.getArgument(0);
                f.setId(99L);
                return f;
            });

            AddressTemplateFieldDto dto = service.createField(new CreateAddressTemplateFieldRequest(
                    "building", "fields.building", "TEXT", "NONE", null));

            assertThat(dto.fieldKey()).isEqualTo("building");
        }

        @Test
        void rejectsDuplicateKey() {
            when(fieldRepository.existsByFieldKey("state")).thenReturn(true);
            assertThatThrownBy(() -> service.createField(new CreateAddressTemplateFieldRequest(
                    "state", "fields.state", "TEXT", "NONE", null)))
                    .isInstanceOf(TemplateConfigException.class);
        }
    }

    @Nested
    @DisplayName("addTemplateField")
    class AddTemplateField {
        @Test
        void addsFieldWithMasterDataWarning() {
            AddressTemplateField field = stateField();
            field.setId(FIELD_STATE);

            when(fieldRepository.findById(FIELD_STATE)).thenReturn(Optional.of(field));
            when(templateRepository.existsByCountryIdAndAddressTemplateField_Id(COUNTRY_TR, FIELD_STATE))
                    .thenReturn(false);
            when(templateRepository.maxSequenceByCountryId(COUNTRY_TR)).thenReturn(5);
            when(coreMasterDataClient.hasMasterData(COUNTRY_TR, "STATE")).thenReturn(false);
            when(templateRepository.save(any())).thenAnswer(inv -> {
                CountryAddressTemplate t = inv.getArgument(0);
                t.setId(TEMPLATE_ID);
                return t;
            });

            TemplateFieldMutationResponse response = service.addTemplateField(COUNTRY_TR,
                    new AddCountryTemplateFieldRequest(FIELD_STATE, false, null, null, null));

            assertThat(response.warning()).isEqualTo("MASTER_DATA_EMPTY");
            assertThat(response.template().getFieldKey()).isEqualTo("state");
        }

        @Test
        void rejectsInvalidRegex() {
            AddressTemplateField field = stateField();
            field.setId(FIELD_STATE);
            when(fieldRepository.findById(FIELD_STATE)).thenReturn(Optional.of(field));
            when(templateRepository.existsByCountryIdAndAddressTemplateField_Id(COUNTRY_TR, FIELD_STATE))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.addTemplateField(COUNTRY_TR,
                    new AddCountryTemplateFieldRequest(FIELD_STATE, false, null, "[invalid(", null)))
                    .isInstanceOf(TemplateConfigException.class);
        }
    }

    @Nested
    @DisplayName("removeTemplateField")
    class RemoveTemplateField {
        @Test
        void blocksWhenChildrenExist() {
            AddressTemplateField city = AddressTemplateField.builder()
                    .fieldKey("city").fieldLabelKey("fields.city")
                    .fieldType(AddressTemplateField.FieldType.FIXED)
                    .masterDataSource(AddressTemplateField.MasterDataSource.NONE)
                    .build();
            city.setId(20L);

            AddressTemplateField district = AddressTemplateField.builder()
                    .fieldKey("district").fieldLabelKey("fields.district")
                    .fieldType(AddressTemplateField.FieldType.MASTER_SELECT)
                    .masterDataSource(AddressTemplateField.MasterDataSource.DISTRICT)
                    .parentFieldKey("city")
                    .build();

            CountryAddressTemplate cityRow = CountryAddressTemplate.builder()
                    .countryId(COUNTRY_TR).addressTemplateField(city).sequence(1).build();
            cityRow.setId(TEMPLATE_ID);

            CountryAddressTemplate districtRow = CountryAddressTemplate.builder()
                    .countryId(COUNTRY_TR).addressTemplateField(district).sequence(2).build();
            districtRow.setId(101L);

            when(templateRepository.findByIdAndCountryId(TEMPLATE_ID, COUNTRY_TR))
                    .thenReturn(Optional.of(cityRow));
            when(templateRepository.findByCountryIdOrderBySequenceAsc(COUNTRY_TR))
                    .thenReturn(List.of(cityRow, districtRow));

            assertThatThrownBy(() -> service.removeTemplateField(COUNTRY_TR, TEMPLATE_ID))
                    .isInstanceOf(TemplateConfigException.class);
            verify(templateRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("copyFrom")
    class CopyFrom {
        @Test
        void copiesAllRows() {
            AddressTemplateField field = stateField();
            field.setId(FIELD_STATE);
            CountryAddressTemplate source = CountryAddressTemplate.builder()
                    .countryId(2L)
                    .addressTemplateField(field)
                    .mandatory(true)
                    .sequence(1)
                    .validationRegex("^[0-9]+$")
                    .errorMessageKey("validation.state")
                    .build();

            when(templateRepository.findByCountryIdOrderBySequenceAsc(2L)).thenReturn(List.of(source));

            CopyTemplateResultDto result = service.copyFrom(COUNTRY_TR, 2L);

            assertThat(result.copiedCount()).isEqualTo(1);
            verify(templateRepository).deleteByCountryId(COUNTRY_TR);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CountryAddressTemplate>> captor = ArgumentCaptor.forClass(List.class);
            verify(templateRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(1);
            assertThat(captor.getValue().get(0).getCountryId()).isEqualTo(COUNTRY_TR);
        }
    }
}

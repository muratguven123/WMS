package com.wms.localization.controller;

import com.wms.localization.dto.address.AddressResponse;
import com.wms.localization.dto.address.CountryAddressTemplateDto;
import com.wms.localization.exception.address.AddressNotFoundException;
import com.wms.localization.exception.handler.GlobalExceptionHandler;
import com.wms.localization.service.address.AddressService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AddressController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("AddressController")
class AddressControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AddressService addressService;

    @Test
    @DisplayName("GET /api/addresses/{id} — adres detayını döner")
    void getById_returnsAddress() throws Exception {
        Long id = 1L;
        Long countryId = 1L;

        AddressResponse response = new AddressResponse(
                id,
                countryId,
                "İstanbul",
                null,
                "34000",
                Map.of("district", "Kadıköy"),
                "Kadıköy, İstanbul 34000"
        );

        when(addressService.getById(id)).thenReturn(response);

        mockMvc.perform(get("/api/addresses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.city").value("İstanbul"))
                .andExpect(jsonPath("$.formattedAddress").value("Kadıköy, İstanbul 34000"));
    }

    @Test
    @DisplayName("GET /api/addresses/{id} — bulunamazsa 404 döner")
    void getById_notFound_returns404() throws Exception {
        Long id = 1L;

        when(addressService.getById(id)).thenThrow(new AddressNotFoundException(id));

        mockMvc.perform(get("/api/addresses/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Address Not Found"));
    }

    @Test
    @DisplayName("GET /api/addresses/templates/{countryId} — şablon alanlarını sıralı döner")
    void getTemplate_returnsOrderedFields() throws Exception {
        Long countryId = 1L;

        CountryAddressTemplateDto district = CountryAddressTemplateDto.builder()
                .fieldKey("district")
                .fieldLabelKey("fields.district")
                .mandatory(true)
                .sequence(1)
                .fieldType("TEXT")
                .masterDataSource("NONE")
                .build();

        when(addressService.getTemplateByCountry(countryId)).thenReturn(List.of(district));

        mockMvc.perform(get("/api/addresses/templates/{countryId}", countryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fieldKey").value("district"))
                .andExpect(jsonPath("$[0].fieldType").value("TEXT"))
                .andExpect(jsonPath("$[0].masterDataSource").value("NONE"))
                .andExpect(jsonPath("$[0].parentFieldKey").doesNotExist())
                .andExpect(jsonPath("$[0].mandatory").value(true));
    }

    @Test
    @DisplayName("GET /api/addresses — kayıtlı adresleri sayfalı listeler")
    void list_returnsPagedAddresses() throws Exception {
        AddressResponse row = new AddressResponse(
                1L,
                1L,
                "İstanbul",
                null,
                "34000",
                Map.of("district", "Kadıköy"),
                "Kadıköy, İstanbul 34000"
        );

        when(addressService.list(isNull(), any())).thenReturn(
                new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("1"))
                .andExpect(jsonPath("$.content[0].formattedAddress").value("Kadıköy, İstanbul 34000"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/addresses/templates/{countryId} — şablonu olmayan ülke için boş liste (404 değil)")
    void getTemplate_returnsEmptyListWhenNoTemplate() throws Exception {
        Long countryId = 999L;

        when(addressService.getTemplateByCountry(countryId)).thenReturn(List.of());

        mockMvc.perform(get("/api/addresses/templates/{countryId}", countryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}

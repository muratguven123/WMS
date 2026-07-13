package com.wms.core.controller;

import com.wms.core.dto.address.CountryDto;
import com.wms.core.exception.GlobalExceptionHandler;
import com.wms.core.security.TenantContextFilter;
import com.wms.core.service.AddressQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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

    @MockBean private AddressQueryService addressQueryService;
    @MockBean private TenantContextFilter tenantContextFilter;

    @Test
    @DisplayName("GET /api/address/countries — aktif ülkeleri listeler")
    void listCountries_returnsCountries() throws Exception {
        Long turkeyId = 1L;
        when(addressQueryService.listCountries()).thenReturn(
                List.of(new CountryDto(turkeyId, "TR", "Türkiye")));

        mockMvc.perform(get("/api/address/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(turkeyId.toString()))
                .andExpect(jsonPath("$[0].isoCode").value("TR"))
                .andExpect(jsonPath("$[0].name").value("Türkiye"));
    }
}

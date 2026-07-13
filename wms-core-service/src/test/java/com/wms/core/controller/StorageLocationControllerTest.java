package com.wms.core.controller;

import com.wms.core.dto.StorageLocationResponse;
import com.wms.core.entity.enums.StorageLocationStatus;
import com.wms.core.exception.GlobalExceptionHandler;
import com.wms.core.security.TenantContextFilter;
import com.wms.core.service.LocationCapacityService;
import com.wms.core.service.StorageLocationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StorageLocationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("StorageLocationController")
class StorageLocationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private StorageLocationService storageLocationService;
    @MockBean private LocationCapacityService locationCapacityService;
    @MockBean private TenantContextFilter tenantContextFilter;

    @BeforeEach
    void setUpSecurity() {
        ControllerTestSecuritySupport.authenticateAsWmsAdmin();
    }

    @AfterEach
    void clearSecurity() {
        ControllerTestSecuritySupport.clearAuthentication();
    }

    @Test
    @DisplayName("GET /api/locations/{id} — lokasyon detayını döner")
    void getById_returnsLocation() throws Exception {
        Long locationId = 1L;
        Long zoneId = 1L;
        Long warehouseLocationId = 1L;

        StorageLocationResponse response = new StorageLocationResponse(
                locationId,
                zoneId,
                warehouseLocationId,
                "ZONE-A",
                "STORAGE",
                "A-01-01-01",
                "A",
                "01",
                "01",
                "01",
                new BigDecimal("100.00"),
                new BigDecimal("500.00"),
                new BigDecimal("25.00"),
                new BigDecimal("100.00"),
                new BigDecimal("25.00"),
                StorageLocationStatus.ACTIVE,
                true
        );

        when(storageLocationService.findById(eq(locationId))).thenReturn(response);

        mockMvc.perform(get("/api/locations/{locationId}", locationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(locationId.toString()))
                .andExpect(jsonPath("$.addressCode").value("A-01-01-01"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true));
    }
}

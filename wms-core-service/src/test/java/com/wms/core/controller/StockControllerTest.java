package com.wms.core.controller;

import com.wms.core.entity.Stock;
import com.wms.core.exception.GlobalExceptionHandler;
import com.wms.core.repository.StockRepository;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextFilter;
import com.wms.core.security.TenantContextHolder;
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

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("StockController")
class StockControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long COMPANY_ID = 1L;
    private static final Long LOCATION_ID = 1L;

    @Autowired private MockMvc mockMvc;

    @MockBean private StockRepository stockRepository;
    @MockBean private TenantContextFilter tenantContextFilter;

    @BeforeEach
    void setContext() {
        ControllerTestSecuritySupport.authenticateAsWmsAdmin();
        TenantContextHolder.setContext(new TenantContext(USER_ID, COMPANY_ID, LOCATION_ID));
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        ControllerTestSecuritySupport.clearAuthentication();
    }

    @Test
    @DisplayName("GET /api/stocks — tenant bağlamıyla stokları döner")
    void getStocks_returnsTenantScopedStocks() throws Exception {
        Long stockId = 1L;
        Stock stock = Stock.builder()
                .sku("SKU-001")
                .quantity(42)
                .build();
        stock.setId(stockId);

        when(stockRepository.findAll()).thenReturn(List.of(stock));

        mockMvc.perform(get("/api/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.locationId").value(LOCATION_ID.toString()))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.stocks[0].id").value(stockId.toString()))
                .andExpect(jsonPath("$.stocks[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.stocks[0].quantity").value(42));
    }
}

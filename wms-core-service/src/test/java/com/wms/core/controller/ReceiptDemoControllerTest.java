package com.wms.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.core.exception.GlobalExceptionHandler;
import com.wms.core.security.TenantContextFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReceiptDemoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("ReceiptDemoController")
class ReceiptDemoControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

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
    @DisplayName("POST /api/demo/receipts — demo form gönderimini kabul eder")
    void submit_returnsAccepted() throws Exception {
        Map<String, Object> body = Map.of(
                "tax_number", "1234567890",
                "zip_code", "34000");

        mockMvc.perform(post("/api/demo/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.screenCode").value("REC_CONTROL_FORM"));
    }
}

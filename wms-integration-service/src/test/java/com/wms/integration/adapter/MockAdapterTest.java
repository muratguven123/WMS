package com.wms.integration.adapter;

import com.wms.integration.adapter.dto.*;
import com.wms.integration.adapter.impl.MockAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockAdapter Unit Tests")
class MockAdapterTest {

    private final MockAdapter mockAdapter = new MockAdapter();

    @Test
    @DisplayName("sendCustomerAccount - returns successful ErpResponse")
    void sendCustomerAccount_returnsSuccess() {
        CustomerAccountDto dto = CustomerAccountDto.builder()
                .companyId(1L)
                .locationId(10L)
                .customerCode("CUST01")
                .name("Test Customer")
                .taxNumber("1234567890")
                .currencyCode("TRY")
                .address("Istanbul, TR")
                .build();

        ErpResponse response = mockAdapter.sendCustomerAccount(dto);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getExternalReference()).isEqualTo("MOCK-CUST-CUST01");
        assertThat(response.getMessage()).contains("Mock customer account sync OK");
    }

    @Test
    @DisplayName("sendPurchaseOrder - returns successful ErpResponse")
    void sendPurchaseOrder_returnsSuccess() {
        PurchaseOrderDto dto = PurchaseOrderDto.builder()
                .companyId(1L)
                .locationId(10L)
                .orderNumber("PO-1001")
                .orderDate(LocalDate.now())
                .customerCode("CUST01")
                .lines(List.of(OrderLineDto.builder()
                        .productCode("PROD01")
                        .quantity(new BigDecimal("5"))
                        .unitPrice(new BigDecimal("10.5"))
                        .currencyCode("USD")
                        .taxRate(new BigDecimal("20"))
                        .build()))
                .build();

        ErpResponse response = mockAdapter.sendPurchaseOrder(dto);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getExternalReference()).isEqualTo("MOCK-PO-PO-1001");
        assertThat(response.getMessage()).contains("Mock purchase order sync OK");
    }

    @Test
    @DisplayName("sendSalesOrder - returns successful ErpResponse")
    void sendSalesOrder_returnsSuccess() {
        SalesOrderDto dto = SalesOrderDto.builder()
                .companyId(1L)
                .locationId(10L)
                .orderNumber("SO-2002")
                .orderDate(LocalDate.now())
                .customerCode("CUST02")
                .lines(List.of(OrderLineDto.builder()
                        .productCode("PROD02")
                        .quantity(new BigDecimal("3"))
                        .unitPrice(new BigDecimal("100"))
                        .currencyCode("EUR")
                        .taxRate(new BigDecimal("10"))
                        .build()))
                .build();

        ErpResponse response = mockAdapter.sendSalesOrder(dto);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getExternalReference()).isEqualTo("MOCK-SO-SO-2002");
        assertThat(response.getMessage()).contains("Mock sales order sync OK");
    }

    @Test
    @DisplayName("sendReturnNotice - returns successful ErpResponse")
    void sendReturnNotice_returnsSuccess() {
        ReturnNoticeDto dto = ReturnNoticeDto.builder()
                .companyId(1L)
                .locationId(10L)
                .referenceOrderNumber("SO-2002")
                .returnReason("Defective")
                .lines(List.of(OrderLineDto.builder()
                        .productCode("PROD02")
                        .quantity(BigDecimal.ONE)
                        .build()))
                .build();

        ErpResponse response = mockAdapter.sendReturnNotice(dto);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getExternalReference()).isEqualTo("MOCK-RET-SO-2002");
        assertThat(response.getMessage()).contains("Mock return notice sync OK");
    }

    @Test
    @DisplayName("sendCountResult - returns successful ErpResponse")
    void sendCountResult_returnsSuccess() {
        CountResultDto dto = CountResultDto.builder()
                .companyId(1L)
                .locationId(10L)
                .countId(55L)
                .countDate(LocalDate.now())
                .lines(List.of(CountLineDto.builder()
                        .productCode("PROD01")
                        .expectedQty(new BigDecimal("10"))
                        .countedQty(new BigDecimal("12"))
                        .difference(new BigDecimal("2"))
                        .build()))
                .build();

        ErpResponse response = mockAdapter.sendCountResult(dto);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getExternalReference()).isEqualTo("MOCK-CNT-55");
        assertThat(response.getMessage()).contains("Mock count result sync OK");
    }

    @Test
    @DisplayName("sendAccountingVoucher - returns successful ErpResponse")
    void sendAccountingVoucher_returnsSuccess() {
        LocalDate date = LocalDate.now();
        AccountingVoucherDto dto = AccountingVoucherDto.builder()
                .companyId(1L)
                .locationId(10L)
                .voucherType("MAHSUP")
                .voucherDate(date)
                .lines(List.of(VoucherLineDto.builder()
                        .accountCode("100")
                        .debit(new BigDecimal("1000"))
                        .credit(BigDecimal.ZERO)
                        .currencyCode("TRY")
                        .exchangeRate(BigDecimal.ONE)
                        .build()))
                .build();

        ErpResponse response = mockAdapter.sendAccountingVoucher(dto);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getExternalReference()).isEqualTo("MOCK-VCH-MAHSUP-" + date);
        assertThat(response.getMessage()).contains("Mock accounting voucher sync OK");
    }

    @Test
    @DisplayName("fetchTaxInfo - returns list of mock TaxInfoDto")
    void fetchTaxInfo_returnsMockTaxInfoList() {
        List<TaxInfoDto> taxInfos = mockAdapter.fetchTaxInfo();

        assertThat(taxInfos).hasSize(2);
        assertThat(taxInfos.get(0).getTaxTypeCode()).isEqualTo("KDV_STANDART");
        assertThat(taxInfos.get(0).getRate()).isEqualByComparingTo("20");
        assertThat(taxInfos.get(1).getTaxTypeCode()).isEqualTo("KDV_INDIRIMLI");
        assertThat(taxInfos.get(1).getRate()).isEqualByComparingTo("10");
    }
}

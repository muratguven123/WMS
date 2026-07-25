package com.wms.billing.service;

import com.wms.billing.domain.entity.Invoice;
import com.wms.billing.domain.enums.InvoiceStatus;
import com.wms.billing.dto.CalculateInvoiceRequest;
import com.wms.billing.dto.CreateInvoiceRequest;
import com.wms.billing.dto.InvoiceDto;
import com.wms.billing.dto.InvoiceItemInputDto;
import com.wms.billing.dto.InvoiceItemResultDto;
import com.wms.billing.dto.InvoiceResponse;
import com.wms.billing.exception.InvoiceNotFoundException;
import com.wms.billing.mapper.InvoiceMapper;
import com.wms.billing.repository.InvoiceRepository;
import com.wms.billing.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceService")
class InvoiceServiceTest {

    private static final Long LOCATION_ID = 10L;
    private static final Long CUSTOMER_ID = 5L;
    private static final LocalDate RATE_DATE = LocalDate.of(2026, 7, 4);

    @Mock private InvoiceCalculationService calculationService;
    @Mock private InvoiceNumberGenerator invoiceNumberGenerator;
    @Mock private InvoiceRepository invoiceRepository;

    private final InvoiceMapper invoiceMapper = new InvoiceMapper();
    private InvoiceService invoiceService;
    private MockedStatic<TenantContextHolder> tenantMock;

    @BeforeEach
    void setUp() {
        tenantMock = mockStatic(TenantContextHolder.class);
        tenantMock.when(TenantContextHolder::getLocationId).thenReturn(LOCATION_ID);
        invoiceService = new InvoiceService(
                calculationService, invoiceNumberGenerator, invoiceRepository, invoiceMapper);
    }

    @AfterEach
    void tearDown() {
        tenantMock.close();
    }

    @Test
    @DisplayName("create — DRAFT fatura persist eder")
    void create_persistsDraftInvoice() {
        CreateInvoiceRequest request = sampleRequest();
        InvoiceDto calculated = sampleCalculated();

        when(calculationService.calculateInvoice(any(), eq(CUSTOMER_ID), eq(LOCATION_ID),
                eq("EUR"), eq(RATE_DATE), isNull())).thenReturn(calculated);
        when(invoiceNumberGenerator.generate(LOCATION_ID)).thenReturn("INV-10-20260704-001");
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice invc = inv.getArgument(0);
            invc.setId(99L);
            return invc;
        });

        InvoiceResponse response = invoiceService.create(request);

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.invoiceNumber()).isEqualTo("INV-10-20260704-001");
        assertThat(response.status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(response.preview()).isFalse();

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        assertThat(captor.getValue().getLocationId()).isEqualTo(LOCATION_ID);
        assertThat(captor.getValue().getItems()).hasSize(1);
    }

    @Test
    @DisplayName("approve — DRAFT faturayı APPROVED yapar")
    void approve_updatesStatus() {
        Invoice invoice = sampleInvoice(InvoiceStatus.DRAFT);
        when(invoiceRepository.findByIdWithItems(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);

        InvoiceResponse response = invoiceService.approve(1L);

        assertThat(response.status()).isEqualTo(InvoiceStatus.APPROVED);
    }

    @Test
    @DisplayName("approve — onaylı faturada hata fırlatır")
    void approve_rejectsNonDraft() {
        Invoice invoice = sampleInvoice(InvoiceStatus.APPROVED);
        when(invoiceRepository.findByIdWithItems(1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.approve(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("getById — farklı lokasyonda InvoiceNotFoundException")
    void getById_rejectsOtherLocation() {
        Invoice invoice = sampleInvoice(InvoiceStatus.DRAFT);
        invoice.setLocationId(999L);
        when(invoiceRepository.findByIdWithItems(1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.getById(1L))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    @DisplayName("calculatePreview — preview=true döner")
    void calculatePreview_returnsPreview() {
        InvoiceDto calculated = sampleCalculated();
        when(calculationService.calculateInvoice(any(), eq(CUSTOMER_ID), eq(LOCATION_ID),
                eq("EUR"), eq(RATE_DATE), isNull())).thenReturn(calculated);

        InvoiceResponse response = invoiceService.calculatePreview(
                CalculateInvoiceRequest.builder()
                        .customerId(CUSTOMER_ID)
                        .invoiceCurrency("EUR")
                        .exchangeRateDate(RATE_DATE)
                        .items(sampleRequest().items())
                        .build());

        assertThat(response.preview()).isTrue();
        assertThat(response.id()).isNull();
        assertThat(response.invoiceNumber()).isNull();
    }

    private CreateInvoiceRequest sampleRequest() {
        return CreateInvoiceRequest.builder()
                .customerId(CUSTOMER_ID)
                .invoiceCurrency("EUR")
                .exchangeRateDate(RATE_DATE)
                .items(List.of(InvoiceItemInputDto.builder()
                        .itemDescription("Hizmet")
                        .quantity(new BigDecimal("1"))
                        .unitPriceOriginal(new BigDecimal("100"))
                        .discountOriginal(BigDecimal.ZERO)
                        .taxTypeCode("KDV")
                        .taxRate(new BigDecimal("20"))
                        .build()))
                .build();
    }

    private InvoiceDto sampleCalculated() {
        return InvoiceDto.builder()
                .customerId(CUSTOMER_ID)
                .locationId(LOCATION_ID)
                .invoiceCurrency("EUR")
                .accountingCurrency("TRY")
                .exchangeRateDate(RATE_DATE)
                .exchangeRateValue(new BigDecimal("36.85"))
                .items(List.of(InvoiceItemResultDto.builder()
                        .itemDescription("Hizmet")
                        .quantity(new BigDecimal("1"))
                        .unitPriceOriginal(new BigDecimal("100"))
                        .discountOriginal(BigDecimal.ZERO)
                        .taxTypeCode("KDV")
                        .taxRate(new BigDecimal("20"))
                        .lineTotalOriginal(new BigDecimal("100"))
                        .taxAmountOriginal(new BigDecimal("20"))
                        .build()))
                .subtotalOriginal(new BigDecimal("100"))
                .taxAmountOriginal(new BigDecimal("20"))
                .grandTotalOriginal(new BigDecimal("120"))
                .grandTotalAccounting(new BigDecimal("4422.00"))
                .status(InvoiceStatus.DRAFT)
                .build();
    }

    private Invoice sampleInvoice(InvoiceStatus status) {
        Invoice invoice = Invoice.builder()
                .id(1L)
                .invoiceNumber("INV-1")
                .customerId(CUSTOMER_ID)
                .locationId(LOCATION_ID)
                .issueDate(LocalDateTime.now())
                .invoiceCurrency("EUR")
                .accountingCurrency("TRY")
                .exchangeRateDate(RATE_DATE)
                .exchangeRateValue(new BigDecimal("36.85"))
                .subtotalOriginal(new BigDecimal("100"))
                .taxAmountOriginal(new BigDecimal("20"))
                .grandTotalOriginal(new BigDecimal("120"))
                .grandTotalAccounting(new BigDecimal("4422.00"))
                .status(status)
                .createdAt(LocalDateTime.now())
                .items(new java.util.ArrayList<>())
                .build();
        return invoice;
    }
}

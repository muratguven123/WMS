package com.wms.finance.service;

import com.wms.finance.dto.OrderRequestDto;
import com.wms.finance.entity.Currency;
import com.wms.finance.exception.InvalidCustomerCurrencyException;
import com.wms.finance.repository.CurrencyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock private CustomerCurrencyValidator currencyValidator;
    @Mock private CurrencyRepository currencyRepository;

    @InjectMocks
    private OrderService orderService;

    private static final Long CUSTOMER_ID = 1L;
    private static final Long CURRENCY_ID = 1L;

    @Test
    @DisplayName("İzinli para birimiyle sipariş oluşturur")
    void createOrder_success() {
        OrderRequestDto request = new OrderRequestDto(
                CUSTOMER_ID, CURRENCY_ID, new BigDecimal("100"), Instant.now(), null);

        Currency currency = Currency.builder().id(CURRENCY_ID).code("EUR").build();
        when(currencyRepository.findById(CURRENCY_ID)).thenReturn(Optional.of(currency));

        var response = orderService.createOrder(request);

        verify(currencyValidator).validateCurrencyPermission(CUSTOMER_ID, CURRENCY_ID);
        assertThat(response.currencyCode()).isEqualTo("EUR");
        assertThat(response.customerId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    @DisplayName("İzinsiz para biriminde sipariş oluşturulmaz")
    void createOrder_rejectsInvalidCurrency() {
        OrderRequestDto request = new OrderRequestDto(
                CUSTOMER_ID, CURRENCY_ID, BigDecimal.TEN, Instant.now(), null);

        doThrow(new InvalidCustomerCurrencyException(CUSTOMER_ID, CURRENCY_ID))
                .when(currencyValidator).validateCurrencyPermission(CUSTOMER_ID, CURRENCY_ID);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InvalidCustomerCurrencyException.class);

        verify(currencyRepository, never()).findById(CURRENCY_ID);
    }
}

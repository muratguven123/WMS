package com.wms.finance.service;

import com.wms.finance.entity.Currency;
import com.wms.finance.entity.FinanceCustomer;
import com.wms.finance.exception.InvalidCustomerCurrencyException;
import com.wms.finance.repository.CustomerPermittedCurrencyRepository;
import com.wms.finance.repository.FinanceCustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerCurrencyValidator")
class CustomerCurrencyValidatorTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long DEFAULT_ID  = 2L;
    private static final Long INVOICE_ID  = 3L;
    private static final Long PERMITTED_ID = 4L;
    private static final Long FORBIDDEN_ID = 99L;

    @Mock private FinanceCustomerRepository customerRepository;
    @Mock private CustomerPermittedCurrencyRepository permittedCurrencyRepository;

    @InjectMocks
    private CustomerCurrencyValidator validator;

    private FinanceCustomer customer() {
        return FinanceCustomer.builder()
                .defaultCurrency(currency(DEFAULT_ID, "EUR"))
                .invoicingCurrency(currency(INVOICE_ID, "TRY"))
                .build();
    }

    private Currency currency(Long id, String code) {
        return Currency.builder().id(id).code(code).active(true).build();
    }

    @Test
    @DisplayName("Varsayılan para birimi izinli kabul edilir")
    void allowsDefaultCurrency() {
        when(customerRepository.findByIdWithCurrency(CUSTOMER_ID))
                .thenReturn(Optional.of(customer()));

        assertThatNoException()
                .isThrownBy(() -> validator.validateCurrencyPermission(CUSTOMER_ID, DEFAULT_ID));
    }

    @Test
    @DisplayName("Fatura para birimi izinli kabul edilir")
    void allowsInvoicingCurrency() {
        when(customerRepository.findByIdWithCurrency(CUSTOMER_ID))
                .thenReturn(Optional.of(customer()));

        assertThatNoException()
                .isThrownBy(() -> validator.validateCurrencyPermission(CUSTOMER_ID, INVOICE_ID));
    }

    @Test
    @DisplayName("İzin tablosundaki para birimi kabul edilir")
    void allowsPermittedCurrency() {
        when(customerRepository.findByIdWithCurrency(CUSTOMER_ID))
                .thenReturn(Optional.of(customer()));
        when(permittedCurrencyRepository.existsByCustomerIdAndCurrencyId(CUSTOMER_ID, PERMITTED_ID))
                .thenReturn(true);

        assertThatNoException()
                .isThrownBy(() -> validator.validateCurrencyPermission(CUSTOMER_ID, PERMITTED_ID));
    }

    @Test
    @DisplayName("İzinsiz para birimi için exception fırlatır")
    void rejectsUnauthorizedCurrency() {
        when(customerRepository.findByIdWithCurrency(CUSTOMER_ID))
                .thenReturn(Optional.of(customer()));
        when(permittedCurrencyRepository.existsByCustomerIdAndCurrencyId(CUSTOMER_ID, FORBIDDEN_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> validator.validateCurrencyPermission(CUSTOMER_ID, FORBIDDEN_ID))
                .isInstanceOf(InvalidCustomerCurrencyException.class);
    }
}

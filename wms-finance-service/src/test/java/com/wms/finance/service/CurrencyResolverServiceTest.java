package com.wms.finance.service;

import com.wms.finance.entity.CompanyCurrencySetting;
import com.wms.finance.entity.Contract;
import com.wms.finance.entity.Currency;
import com.wms.finance.entity.FinanceCustomer;
import com.wms.finance.entity.LocationCurrencySetting;
import com.wms.finance.entity.SystemConfig;
import com.wms.finance.exception.CurrencyNotConfiguredException;
import com.wms.finance.repository.CompanyCurrencySettingRepository;
import com.wms.finance.repository.ContractRepository;
import com.wms.finance.repository.FinanceCustomerRepository;
import com.wms.finance.repository.LocationCurrencySettingRepository;
import com.wms.finance.repository.SystemConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurrencyResolverService")
class CurrencyResolverServiceTest {

    @Mock private ContractRepository contractRepository;
    @Mock private FinanceCustomerRepository customerRepository;
    @Mock private LocationCurrencySettingRepository locationRepository;
    @Mock private CompanyCurrencySettingRepository companyRepository;
    @Mock private SystemConfigRepository systemConfigRepository;

    @InjectMocks
    private CurrencyResolverService resolverService;

    private static final Long COMPANY_ID  = 201L;
    private static final Long LOCATION_ID = 101L;
    private static final Long CUSTOMER_ID = 3001L;
    private static final Long CONTRACT_ID = 4001L;

    private Currency currency(String code) {
        return Currency.builder().code(code).active(true).decimalPlaces(2).build();
    }

    @Test
    @DisplayName("Sözleşme varsa sözleşme para birimi kazanır")
    void resolve_prefersContractCurrency() {
        Contract contract = Contract.builder().currency(currency("USD")).build();
        when(contractRepository.findByIdWithCurrency(CONTRACT_ID)).thenReturn(Optional.of(contract));

        Currency result = resolverService.resolveTransactionCurrency(
                COMPANY_ID, LOCATION_ID, CUSTOMER_ID, CONTRACT_ID);

        assertThat(result.getCode()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Sözleşme yoksa müşteri varsayılanı kullanılır")
    void resolve_fallsBackToCustomer() {
        FinanceCustomer customer = FinanceCustomer.builder().defaultCurrency(currency("EUR")).build();
        when(customerRepository.findByIdWithCurrency(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        Currency result = resolverService.resolveTransactionCurrency(
                COMPANY_ID, LOCATION_ID, CUSTOMER_ID, null);

        assertThat(result.getCode()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Müşteri yoksa depo yerel para birimi kullanılır")
    void resolve_fallsBackToLocation() {
        LocationCurrencySetting location = LocationCurrencySetting.builder()
                .localCurrency(currency("TRY"))
                .build();
        when(locationRepository.findByLocationIdWithCurrency(LOCATION_ID))
                .thenReturn(Optional.of(location));

        Currency result = resolverService.resolveTransactionCurrency(
                COMPANY_ID, LOCATION_ID, null, null);

        assertThat(result.getCode()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("Depo yoksa şirket ana para birimi kullanılır")
    void resolve_fallsBackToCompany() {
        CompanyCurrencySetting company = CompanyCurrencySetting.builder()
                .baseCurrency(currency("TRY"))
                .build();
        when(companyRepository.findByCompanyIdWithCurrency(COMPANY_ID))
                .thenReturn(Optional.of(company));

        Currency result = resolverService.resolveTransactionCurrency(
                COMPANY_ID, null, null, null);

        assertThat(result.getCode()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("Hiçbir seviye yoksa sistem varsayılanı kullanılır")
    void resolve_fallsBackToSystemDefault() {
        SystemConfig config = SystemConfig.builder().defaultCurrency(currency("TRY")).build();
        when(systemConfigRepository.findFirstWithDefaultCurrency()).thenReturn(Optional.of(config));

        Currency result = resolverService.resolveTransactionCurrency(null, null, null, null);

        assertThat(result.getCode()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("Hiçbir kaynak yoksa CurrencyNotConfiguredException fırlatır")
    void resolve_throwsWhenNothingConfigured() {
        when(systemConfigRepository.findFirstWithDefaultCurrency()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolverService.resolveTransactionCurrency(null, null, null, null))
                .isInstanceOf(CurrencyNotConfiguredException.class);
    }
}

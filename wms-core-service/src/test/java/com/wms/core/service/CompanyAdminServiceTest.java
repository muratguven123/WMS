package com.wms.core.service;

import com.wms.core.dto.company.CompanyAdminDto;
import com.wms.core.dto.company.CompanyUsageDto;
import com.wms.core.dto.company.CreateCompanyRequest;
import com.wms.core.dto.company.UpdateCompanyRequest;
import com.wms.core.entity.Company;
import com.wms.core.entity.Organization;
import com.wms.core.event.ConfigChangeEvent;
import com.wms.core.exception.BusinessException;
import com.wms.core.messaging.CompanyChangedEventFactory;
import com.wms.core.repository.CompanyRepository;
import com.wms.core.repository.LocationRepository;
import com.wms.core.repository.OrganizationRepository;
import com.wms.core.repository.TransactionLogRepository;
import com.wms.core.repository.UserAccessRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyAdminService")
class CompanyAdminServiceTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private UserAccessRepository userAccessRepository;
    @Mock private TransactionLogRepository transactionLogRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CompanyChangedEventFactory companyChangedEventFactory;

    @InjectMocks
    private CompanyAdminService companyAdminService;

    private Organization org(Long id, String name) {
        Organization o = Organization.builder().name(name).build();
        o.setId(id);
        return o;
    }

    private Company company(Long id, Organization organization, String name, String tax) {
        Company c = Company.builder()
                .organization(organization)
                .name(name)
                .taxNumber(tax)
                .taxOffice("Kadıköy")
                .build();
        c.setId(id);
        c.setActive(true);
        return c;
    }

    @Nested
    @DisplayName("createCompany")
    class CreateCompany {

        @Test
        @DisplayName("vergi no normalize edilir ve kayıt oluşturulur")
        void createsCompany() {
            Organization organization = org(1L, "Demo Holding");
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
            when(companyRepository.existsByTaxNumberIncludingInactive("ABC1234567")).thenReturn(false);
            when(companyRepository.save(any(Company.class))).thenAnswer(inv -> {
                Company c = inv.getArgument(0);
                c.setId(99L);
                return c;
            });
            when(locationRepository.countActiveByCompanyId(99L)).thenReturn(0L);

            CompanyAdminDto dto = companyAdminService.createCompany(
                    new CreateCompanyRequest(1L, "  Acme  ", "abc1234567", "  Beşiktaş  "));

            assertThat(dto.name()).isEqualTo("Acme");
            assertThat(dto.taxNumber()).isEqualTo("ABC1234567");
            assertThat(dto.taxOffice()).isEqualTo("Beşiktaş");
            assertThat(dto.active()).isTrue();
            verify(eventPublisher).publishEvent(any(ConfigChangeEvent.class));
            verify(companyChangedEventFactory).publish(1L, 99L, "CREATE", "Acme", true);
        }

        @Test
        @DisplayName("mükerrer vergi no → 409 COMPANY_TAX_EXISTS")
        void rejectsDuplicateTax() {
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(org(1L, "Demo")));
            when(companyRepository.existsByTaxNumberIncludingInactive("1234567890")).thenReturn(true);

            assertThatThrownBy(() -> companyAdminService.createCompany(
                    new CreateCompanyRequest(1L, "Acme", "1234567890", null)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo("COMPANY_TAX_EXISTS"));
            verify(companyRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deactivateCompany")
    class DeactivateCompany {

        @Test
        @DisplayName("aktif depo varken 409 COMPANY_IN_USE")
        void blocksWhenLocationsExist() {
            Organization organization = org(1L, "Demo");
            Company company = company(10L, organization, "Acme", "1234567890");
            when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
            when(locationRepository.countActiveByCompanyId(10L)).thenReturn(2L);
            when(userAccessRepository.countByCompanyId(10L)).thenReturn(0L);

            assertThatThrownBy(() -> companyAdminService.deactivateCompany(10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo("COMPANY_IN_USE"));
            verify(companyRepository, never()).delete(any());
        }

        @Test
        @DisplayName("bağımlılık yoksa soft-delete yapılır")
        void softDeletesWhenIdle() {
            Organization organization = org(1L, "Demo");
            Company company = company(10L, organization, "Acme", "1234567890");
            when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
            when(locationRepository.countActiveByCompanyId(10L)).thenReturn(0L);
            when(userAccessRepository.countByCompanyId(10L)).thenReturn(0L);

            companyAdminService.deactivateCompany(10L);

            verify(companyRepository).delete(company);
            verify(eventPublisher).publishEvent(any(ConfigChangeEvent.class));
        }
    }

    @Nested
    @DisplayName("getCompanyUsage")
    class GetUsage {

        @Test
        @DisplayName("canDeactivate yalnızca bağımlılık yoksa true")
        void reportsCanDeactivate() {
            Organization organization = org(1L, "Demo");
            Company company = company(10L, organization, "Acme", "1234567890");
            when(companyRepository.findByIdIncludingInactive(10L)).thenReturn(Optional.of(company));
            when(locationRepository.countActiveByCompanyId(10L)).thenReturn(0L);
            when(userAccessRepository.countByCompanyId(10L)).thenReturn(1L);
            when(transactionLogRepository.countByCompanyId(10L)).thenReturn(5L);

            CompanyUsageDto usage = companyAdminService.getCompanyUsage(10L);

            assertThat(usage.activeLocationCount()).isZero();
            assertThat(usage.userAccessCount()).isEqualTo(1L);
            assertThat(usage.transactionLogCount()).isEqualTo(5L);
            assertThat(usage.canDeactivate()).isFalse();
        }
    }

    @Nested
    @DisplayName("updateCompany")
    class UpdateCompany {

        @Test
        @DisplayName("vergi no çakışmasında 409")
        void rejectsDuplicateTaxOnUpdate() {
            Organization organization = org(1L, "Demo");
            Company company = company(10L, organization, "Acme", "OLDTAX1111");
            when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
            when(companyRepository.existsByTaxNumberIncludingInactiveExcludingId("NEWTAX2222", 10L))
                    .thenReturn(true);

            assertThatThrownBy(() -> companyAdminService.updateCompany(10L,
                    new UpdateCompanyRequest("Acme", "newtax2222", "Ofis")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo("COMPANY_TAX_EXISTS"));
        }
    }
}

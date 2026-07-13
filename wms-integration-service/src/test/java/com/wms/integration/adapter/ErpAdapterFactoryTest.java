package com.wms.integration.adapter;

import com.wms.integration.adapter.impl.MockAdapter;
import com.wms.integration.entity.IntegrationSystem;
import com.wms.integration.entity.LocationIntegrationConfig;
import com.wms.integration.repository.LocationIntegrationConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErpAdapterFactoryTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private LocationIntegrationConfigRepository configRepository;

    @InjectMocks
    private ErpAdapterFactory factory;

    @Test
    void getAdapter_resolvesBeanByErpCode() {
        Long locationId = 1L;
        IntegrationSystem system = IntegrationSystem.builder().code("MOCK").name("Mock").build();
        LocationIntegrationConfig config = LocationIntegrationConfig.builder()
                .locationId(locationId)
                .integrationSystem(system)
                .build();
        MockAdapter mockAdapter = new MockAdapter();

        when(configRepository.findActiveByLocationId(locationId)).thenReturn(Optional.of(config));
        when(applicationContext.getBean("mockAdapter", ErpAdapter.class)).thenReturn(mockAdapter);

        ErpAdapter adapter = factory.getAdapter(locationId);

        assertThat(adapter).isSameAs(mockAdapter);
    }

    @Test
    void getAdapter_whenNoConfig_throwsNotFound() {
        Long locationId = 1L;
        when(configRepository.findActiveByLocationId(locationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> factory.getAdapter(locationId))
                .isInstanceOf(ErpAdapterFactory.ErpAdapterNotFoundException.class)
                .hasMessageContaining("No active ERP integration config");
    }

    @Test
    void getAdapter_whenBeanMissing_throwsNotFound() {
        Long locationId = 1L;
        IntegrationSystem system = IntegrationSystem.builder().code("ORACLE").name("Oracle").build();
        LocationIntegrationConfig config = LocationIntegrationConfig.builder()
                .locationId(locationId)
                .integrationSystem(system)
                .build();

        when(configRepository.findActiveByLocationId(locationId)).thenReturn(Optional.of(config));
        when(applicationContext.getBean("oracleAdapter", ErpAdapter.class))
                .thenThrow(new org.springframework.beans.factory.NoSuchBeanDefinitionException("oracleAdapter"));

        assertThatThrownBy(() -> factory.getAdapter(locationId))
                .isInstanceOf(ErpAdapterFactory.ErpAdapterNotFoundException.class)
                .hasMessageContaining("oracleAdapter");
    }
}

package com.wms.core.config;

import com.wms.core.entity.listener.LocationProcessStepConfigAuditListener;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.internal.SessionFactoryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * JPA Entity Listener'larını Spring context'e bağlar.
 *
 * <p>JPA spesifikasyonu, entity listener'ların Spring tarafından değil
 * JPA container tarafından instantiate edilmesini şart koşar.
 * Bu nedenle {@link LocationProcessStepConfigAuditListener} içindeki
 * {@code @Autowired} field injection'ı Spring'in
 * {@code AutowireCapableBeanFactory} desteğiyle çalışmak yerine,
 * listener'ın Spring {@code @Component} olarak tanımlanıp
 * {@code ApplicationEventPublisher}'ı static alana inject etmesi tercih edilmiştir.
 * Bu config sınıfı o bean'in Spring tarafından başlatılmasını güvence altına alır.</p>
 */
@Configuration
public class JpaListenerConfig {

    /**
     * Listener'ı Spring bean olarak kayıt eder.
     * {@code @Autowired setEventPublisher()} bu bean oluşturulduğunda tetiklenir.
     */
    @Bean
    public LocationProcessStepConfigAuditListener locationProcessStepConfigAuditListener() {
        return new LocationProcessStepConfigAuditListener();
    }

    @Bean
    public com.wms.core.entity.listener.ProcessStepDefinitionAuditListener processStepDefinitionAuditListener() {
        return new com.wms.core.entity.listener.ProcessStepDefinitionAuditListener();
    }
}

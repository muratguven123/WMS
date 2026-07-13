package com.wms.outbound.config;

import com.wms.outbound.messaging.TaskEventPublisher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.mock;

@TestConfiguration
@Profile("test")
public class OutboundTestMessagingConfig {

    @Bean
    @Primary
    public TaskEventPublisher taskEventPublisher() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        return new TaskEventPublisher(kafkaTemplate);
    }
}

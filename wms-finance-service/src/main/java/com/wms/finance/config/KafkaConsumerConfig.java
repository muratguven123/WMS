package com.wms.finance.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.events.DomainEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
@Profile("!test")
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, DomainEvent> domainEventConsumerFactory(
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper) {

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.remove(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);
        props.keySet().removeIf(key -> key.toString().startsWith("spring.json."));

        JsonDeserializer<DomainEvent> deserializer = new JsonDeserializer<>(DomainEvent.class, objectMapper);
        deserializer.setUseTypeHeaders(false);
        deserializer.addTrustedPackages("com.wms.events");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DomainEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, DomainEvent> domainEventConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, DomainEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(domainEventConsumerFactory);
        return factory;
    }
}

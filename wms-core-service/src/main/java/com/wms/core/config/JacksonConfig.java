package com.wms.core.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * Jackson serializasyon konfigürasyonu.
 *
 * <p>Tüm {@code Instant} ve {@code OffsetDateTime} alanları JSON'a
 * {@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'} formatında ve UTC offset'iyle yazılır.
 * Bu sayede istemci tarafında timezone kayması (date-shift) oluşmaz.</p>
 */
@Configuration
public class JacksonConfig {

    /**
     * ISO-8601 UTC formatı: 2025-07-04T10:30:00.000Z
     */
    static final DateTimeFormatter UTC_FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .toFormatter()
                    .withZone(ZoneOffset.UTC);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonDateTimeCustomizer() {
        SimpleModule utcTimeModule = new SimpleModule("UtcTimeModule");
        utcTimeModule.addSerializer(Instant.class, new UtcInstantSerializer());
        utcTimeModule.addSerializer(OffsetDateTime.class, new UtcOffsetDateTimeSerializer());

        return builder -> {
            builder.modules(utcTimeModule);
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID);
        };
    }

    private static final class UtcInstantSerializer extends JsonSerializer<Instant> {
        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            gen.writeString(UTC_FORMATTER.format(value));
        }
    }

    private static final class UtcOffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {
        @Override
        public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            gen.writeString(UTC_FORMATTER.format(value.toInstant()));
        }
    }
}

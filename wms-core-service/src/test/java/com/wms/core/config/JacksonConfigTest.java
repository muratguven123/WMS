package com.wms.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = JacksonConfig.class)
@Import({JacksonAutoConfiguration.class, JacksonConfig.class})
class JacksonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serializesInstantAsUtcIsoString() throws Exception {
        Instant instant = Instant.parse("2026-01-10T12:30:45.123Z");

        String json = objectMapper.writeValueAsString(instant);

        assertThat(json).isEqualTo("\"2026-01-10T12:30:45.123Z\"");
    }

    @Test
    void serializesOffsetDateTimeAsUtcIsoString() throws Exception {
        OffsetDateTime value = OffsetDateTime.of(2026, 1, 10, 15, 30, 45, 123_000_000, ZoneOffset.ofHours(3));

        String json = objectMapper.writeValueAsString(value);

        assertThat(json).isEqualTo("\"2026-01-10T12:30:45.123Z\"");
    }
}

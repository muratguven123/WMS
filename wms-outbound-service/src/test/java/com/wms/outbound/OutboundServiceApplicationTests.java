package com.wms.outbound;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.wms.outbound.config.OutboundTestMessagingConfig;

@SpringBootTest
@ActiveProfiles("test")
@Import(OutboundTestMessagingConfig.class)
class OutboundServiceApplicationTests extends OutboundPostgresTestBase {

    @Test
    void contextLoads() {
    }
}

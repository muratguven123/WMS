package com.wms.integration.outbox;

import com.wms.integration.adapter.dto.ErpResponse;
import com.wms.integration.entity.IntegrationSystem;
import com.wms.integration.entity.LocationIntegrationConfig;
import com.wms.integration.entity.OutboxMessage;
import com.wms.integration.entity.enums.ConnectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookDispatcher Unit Tests")
class WebhookDispatcherTest {

    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    private WebhookDispatcher webhookDispatcher;
    private OutboxMessage message;
    private LocationIntegrationConfig config;
    private IntegrationSystem integrationSystem;

    private final String secret = "super-secret-key";
    private final String url = "https://erp.wms-target.com/callback";

    @BeforeEach
    void setUp() {
        webhookDispatcher = new WebhookDispatcher(webClient);

        message = OutboxMessage.builder()
                .jobCode("CUSTOMER_SYNC")
                .payload("{\"customerCode\":\"CUST-001\"}")
                .locationId(10L)
                .build();
        message.setId(1001L);

        integrationSystem = IntegrationSystem.builder()
                .code("MOCK-ERP")
                .name("Mock ERP System")
                .webhookUrl(url)
                .webhookSecret(secret)
                .build();

        config = LocationIntegrationConfig.builder()
                .locationId(10L)
                .connectionType(ConnectionType.WEBHOOK)
                .integrationSystem(integrationSystem)
                .build();
    }

    // =====================================================================
    // İmza Doğrulama Testleri
    // =====================================================================

    @Test
    @DisplayName("computeSignature & verifySignature - imza üretir ve doğru şekilde doğrular")
    void signature_computeAndVerify_isSuccessful() {
        long timestamp = Instant.now().getEpochSecond();
        String payload = "{\"data\":\"test\"}";

        String signature = WebhookDispatcher.computeSignature(secret, timestamp, payload);
        assertThat(signature).isNotBlank();

        String headerValue = WebhookDispatcher.SIGNATURE_VERSION + "=" + signature;
        boolean verified = WebhookDispatcher.verifySignature(secret, timestamp, payload, headerValue);
        assertThat(verified).isTrue();
    }

    @Test
    @DisplayName("verifySignature - geçersiz imza veya başlık biçimi durumunda false döner")
    void verifySignature_withInvalidSignatureOrHeader_returnsFalse() {
        long timestamp = Instant.now().getEpochSecond();
        String payload = "{\"data\":\"test\"}";

        // Geçersiz başlık formatı (v1= yok)
        boolean verified1 = WebhookDispatcher.verifySignature(secret, timestamp, payload, "invalid-header-format");
        assertThat(verified1).isFalse();

        // Yanlış signature
        boolean verified2 = WebhookDispatcher.verifySignature(secret, timestamp, payload, "v1=wrongsignature");
        assertThat(verified2).isFalse();
    }

    // =====================================================================
    // Dispatch Testleri
    // =====================================================================

    @Test
    @DisplayName("dispatch - happy path - webhook hedefine imzalı POST atar")
    void dispatch_happyPath_sendsPostAndReturnsSuccess() {
        // GIVEN
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(url)).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(message.getPayload())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        // WHEN
        ErpResponse response = webhookDispatcher.dispatch(message, config);

        // THEN
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getExternalReference()).isEqualTo("WEBHOOK-1001");
        assertThat(response.getMessage()).contains("Webhook delivered to " + url);

        verify(requestBodySpec).header(eq(WebhookDispatcher.HEADER_EVENT), eq("CUSTOMER_SYNC"));
        verify(requestBodySpec).header(eq(WebhookDispatcher.HEADER_MESSAGE_ID), eq("1001"));
        verify(requestBodySpec).header(eq(WebhookDispatcher.HEADER_TIMESTAMP), anyString());
        verify(requestBodySpec).header(eq(WebhookDispatcher.HEADER_SIGNATURE), startsWith("v1="));
    }

    @Test
    @DisplayName("dispatch - webhookUrl eksik ise hata döner")
    void dispatch_missingWebhookUrl_returnsFailure() {
        integrationSystem.setWebhookUrl(null);

        ErpResponse response = webhookDispatcher.dispatch(message, config);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("WEBHOOK_CONFIG_MISSING");
        assertThat(response.getMessage()).contains("webhookUrl is not configured");
    }

    @Test
    @DisplayName("dispatch - webhookSecret eksik ise hata döner")
    void dispatch_missingWebhookSecret_returnsFailure() {
        integrationSystem.setWebhookSecret("");

        ErpResponse response = webhookDispatcher.dispatch(message, config);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("WEBHOOK_CONFIG_MISSING");
        assertThat(response.getMessage()).contains("webhookSecret is not configured");
    }

    @Test
    @DisplayName("dispatch - HTTP hata kodu döndüğünde hatayı sarmalar")
    void dispatch_httpError_returnsFailure() {
        // GIVEN
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(url)).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(message.getPayload())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        WebClientResponseException ex = WebClientResponseException.create(
                400, "Bad Request", org.springframework.http.HttpHeaders.EMPTY, "Invalid payload".getBytes(), null);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.error(ex));

        // WHEN
        ErpResponse response = webhookDispatcher.dispatch(message, config);

        // THEN
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("400");
        assertThat(response.getMessage()).contains("Webhook target returned HTTP 400");
    }

    @Test
    @DisplayName("dispatch - beklenmeyen bir istisna fırlatıldığında hatayı sarmalar")
    void dispatch_unexpectedError_returnsFailure() {
        // GIVEN
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(url)).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(message.getPayload())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.error(new RuntimeException("Connection timeout")));

        // WHEN
        ErpResponse response = webhookDispatcher.dispatch(message, config);

        // THEN
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("WEBHOOK_ERR");
        assertThat(response.getMessage()).isEqualTo("Connection timeout");
    }
}

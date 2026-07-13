package com.wms.billing.config;

import com.wms.billing.security.TenantContext;
import com.wms.billing.security.TenantContextFilter;
import com.wms.billing.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OutboundAuthRequestInterceptor")
class OutboundAuthRequestInterceptorTest {

    private final OutboundAuthRequestInterceptor interceptor = new OutboundAuthRequestInterceptor();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("JWT ve tenant header'larını iletir")
    void forwardsJwtAndTenantHeaders() throws IOException {
        Jwt jwt = Jwt.withTokenValue("test-jwt-token")
                .header("alg", "none")
                .claim("sub", "user-1")
                .claim("wms_user_id", "1")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        TenantContextHolder.setContext(new TenantContext(1L, 10L, 20L));

        MockClientHttpRequest outbound = new MockClientHttpRequest();
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(execution.execute(any(HttpRequest.class), any())).thenReturn(response);

        interceptor.intercept(outbound, new byte[0], execution);

        assertThat(outbound.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer test-jwt-token");
        assertThat(outbound.getHeaders().getFirst(TenantContextFilter.HEADER_COMPANY_ID))
                .isEqualTo("10");
        assertThat(outbound.getHeaders().getFirst(TenantContextFilter.HEADER_LOCATION_ID))
                .isEqualTo("20");
    }

    @Test
    @DisplayName("SecurityContext yoksa gelen istekten Authorization header'ını iletir")
    void forwardsAuthorizationFromIncomingRequestWhenSecurityContextMissing() throws IOException {
        MockHttpServletRequest incoming = new MockHttpServletRequest();
        incoming.addHeader(HttpHeaders.AUTHORIZATION, "Bearer incoming-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));

        MockClientHttpRequest outbound = new MockClientHttpRequest();
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(execution.execute(any(HttpRequest.class), any())).thenReturn(response);

        interceptor.intercept(outbound, new byte[0], execution);

        assertThat(outbound.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer incoming-token");
    }
}

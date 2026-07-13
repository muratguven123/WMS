package com.wms.billing.config;

import com.wms.billing.security.TenantContextFilter;
import com.wms.billing.security.TenantContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

/**
 * billing-service → finance-service gibi iç çağrılarda kullanıcının JWT'sini
 * ve aktif tenant header'larını iletir.
 */
@Component
public class OutboundAuthRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        propagateAuthorization(request);
        propagateTenantHeaders(request);
        return execution.execute(request, body);
    }

    private void propagateAuthorization(HttpRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            request.getHeaders().setBearerAuth(jwtAuth.getToken().getTokenValue());
            return;
        }

        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String authorization = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && !authorization.isBlank()) {
                request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
    }

    private void propagateTenantHeaders(HttpRequest request) {
        TenantContextHolder.getContext().ifPresent(ctx -> {
            request.getHeaders().set(TenantContextFilter.HEADER_COMPANY_ID, String.valueOf(ctx.companyId()));
            request.getHeaders().set(TenantContextFilter.HEADER_LOCATION_ID, String.valueOf(ctx.locationId()));
        });
    }
}

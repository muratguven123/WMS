package com.wms.outbound.integration;

import com.wms.outbound.dto.StorageLocationResponse;
import com.wms.outbound.dto.WorkflowEnforceResponse;
import com.wms.outbound.exception.ApprovalRequiredException;
import com.wms.outbound.exception.BusinessException;
import com.wms.outbound.security.TenantContextFilter;
import com.wms.outbound.security.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class CoreServiceClient {

    private final WebClient coreWebClient;

    public CoreServiceClient(@Qualifier("coreWebClient") WebClient coreWebClient) {
        this.coreWebClient = coreWebClient;
    }

    public StorageLocationResponse getStorageLocation(Long locationId) {
        log.info("Fetching storage location details for id: {}", locationId);
        try {
            return coreWebClient.get()
                    .uri("/api/locations/{locationId}", locationId)
                    .headers(this::applyAuthAndTenantHeaders)
                    .retrieve()
                    .bodyToMono(StorageLocationResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch storage location details for id: {}", locationId, e);
            throw new RuntimeException("Core service communication error: " + e.getMessage(), e);
        }
    }

    /**
     * Core workflow motoruna senkron adım zorlaması. BYPASSED/ALLOWED devam;
     * APPROVAL_REQUIRED → {@link ApprovalRequiredException}.
     */
    public WorkflowEnforceResponse enforceWorkflowStep(
            String processCode, String stepCode, Long referenceId, String referenceType) {
        log.info("Enforcing workflow step process={} step={} ref={}", processCode, stepCode, referenceId);

        Map<String, Object> body = new HashMap<>();
        body.put("processCode", processCode);
        body.put("stepCode", stepCode);
        body.put("referenceId", referenceId);
        body.put("referenceType", referenceType);

        try {
            WorkflowEnforceResponse response = coreWebClient.post()
                    .uri("/api/workflow/enforce")
                    .headers(this::applyAuthAndTenantHeaders)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(WorkflowEnforceResponse.class)
                    .block();

            if (response == null) {
                throw new BusinessException("Empty workflow enforce response from core", HttpStatus.BAD_GATEWAY);
            }

            if (response.decision() == WorkflowEnforceResponse.Decision.APPROVAL_REQUIRED) {
                throw new ApprovalRequiredException(response.approvalRequestId());
            }

            return response;
        } catch (ApprovalRequiredException | BusinessException ex) {
            throw ex;
        } catch (WebClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            log.error("Workflow enforce rejected: status={} body={}", status, ex.getResponseBodyAsString());
            throw new BusinessException(
                    "Workflow enforce failed: " + ex.getResponseBodyAsString(),
                    HttpStatus.valueOf(status.value()));
        } catch (Exception e) {
            log.error("Failed to enforce workflow step process={} step={}", processCode, stepCode, e);
            throw new BusinessException(
                    "Core workflow communication error: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY);
        }
    }

    private void applyAuthAndTenantHeaders(HttpHeaders headers) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            headers.setBearerAuth(jwtAuth.getToken().getTokenValue());
        } else if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String authorization = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && !authorization.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, authorization);
            }
        }

        TenantContextHolder.getContext().ifPresent(ctx -> {
            headers.set(TenantContextFilter.HEADER_COMPANY_ID, String.valueOf(ctx.companyId()));
            headers.set(TenantContextFilter.HEADER_LOCATION_ID, String.valueOf(ctx.locationId()));
        });
    }
}

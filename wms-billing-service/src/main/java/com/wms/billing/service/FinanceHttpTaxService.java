package com.wms.billing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * wms-finance-service vergi motoru HTTP istemcisi.
 */
@Slf4j
@Service
public class FinanceHttpTaxService implements TaxLookupService {

    private final RestTemplate restTemplate;

    @Value("${billing.finance-service.url:http://localhost:8083}")
    private String financeServiceUrl;

    public FinanceHttpTaxService(RestTemplate billingRestTemplate) {
        this.restTemplate = billingRestTemplate;
    }

    @Override
    public TaxLineResult calculateLineTax(
            BigDecimal amount,
            String taxTypeCode,
            Long countryId,
            Long locationId,
            Long customerId,
            String productType,
            String operationType,
            LocalDate transactionDate) {

        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("taxTypeCode", taxTypeCode);
        body.put("mode", "EXCLUSIVE");
        body.put("countryId", countryId);
        body.put("locationId", locationId);
        body.put("customerId", customerId);
        body.put("productType", productType);
        body.put("operationType", operationType);
        body.put("transactionDate", transactionDate);

        return postTax(financeServiceUrl + "/api/taxes/calculate", body);
    }

    @Override
    public TaxLineResult calculateExchangeDifferenceTax(
            BigDecimal exchangeDifferenceAmount,
            String taxTypeCode,
            LocalDate date,
            Long locationId,
            Long countryId) {

        Map<String, Object> body = new HashMap<>();
        body.put("exchangeDifferenceAmount", exchangeDifferenceAmount);
        body.put("taxTypeCode", taxTypeCode);
        body.put("date", date);
        body.put("locationId", locationId);
        body.put("countryId", countryId);

        return postTax(financeServiceUrl + "/api/taxes/exchange-difference", body);
    }

    private TaxLineResult postTax(String url, Map<String, Object> body) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body), Map.class);
            Map<?, ?> respBody = response.getBody();
            if (respBody == null) {
                throw new IllegalStateException("Finance tax response empty");
            }
            return new TaxLineResult(
                    toDecimal(respBody.get("net")),
                    toDecimal(respBody.get("tax")),
                    toDecimal(respBody.get("gross")),
                    respBody.get("taxTypeCode") != null ? respBody.get("taxTypeCode").toString() : null,
                    respBody.get("rate") != null ? toDecimal(respBody.get("rate")) : null,
                    Boolean.TRUE.equals(respBody.get("inclusive")));
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            log.error("Finance tax yetkilendirilemedi ({}): {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new IllegalStateException("Finance service vergi sorgusu yetkilendirilemedi");
        } catch (HttpClientErrorException ex) {
            log.error("Finance tax hata ({}): {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new IllegalStateException("Finance service vergi hesabı başarısız: " + ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            log.error("Finance tax iletişim hatası: {}", ex.getMessage());
            throw new IllegalStateException("Finance service vergi sorgusu başarısız: " + ex.getMessage());
        }
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }
}

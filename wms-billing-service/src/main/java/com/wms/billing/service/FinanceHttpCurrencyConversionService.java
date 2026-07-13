package com.wms.billing.service;

import com.wms.billing.domain.enums.RateType;
import com.wms.billing.exception.ExchangeRateNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * wms-finance-service üzerinden kur çözümleyen implementasyon.
 */
@Slf4j
@Service
public class FinanceHttpCurrencyConversionService implements CurrencyConversionService {

    private final RestTemplate restTemplate;

    @Value("${billing.finance-service.url:http://localhost:8083}")
    private String financeServiceUrl;

    public FinanceHttpCurrencyConversionService(RestTemplate billingRestTemplate) {
        this.restTemplate = billingRestTemplate;
    }

    @Override
    public BigDecimal getRate(String fromCurrency, String toCurrency, LocalDate rateDate, RateType rateType) {
        String url = UriComponentsBuilder
                .fromHttpUrl(financeServiceUrl + "/api/rates/lookup")
                .queryParam("fromCurrency", fromCurrency)
                .queryParam("toCurrency", toCurrency)
                .queryParam("rateDate", rateDate)
                .queryParam("rateType", rateType.name())
                .toUriString();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            if (body == null || body.get("rate") == null) {
                throw new ExchangeRateNotFoundException(fromCurrency, toCurrency, rateDate, rateType);
            }
            return new BigDecimal(body.get("rate").toString());
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ExchangeRateNotFoundException(fromCurrency, toCurrency, rateDate, rateType);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            log.error("Finance service kur sorgusu yetkilendirilemedi ({}): {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new IllegalStateException("Finance service kur sorgusu yetkilendirilemedi");
        } catch (RestClientException ex) {
            log.error("Finance service kur sorgusu başarısız: {}", ex.getMessage());
            throw new IllegalStateException("Finance service kur sorgusu başarısız: " + ex.getMessage());
        }
    }
}

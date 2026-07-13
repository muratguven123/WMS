package com.wms.billing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Configuration
public class BillingHttpConfig {

    @Bean
    public RestTemplate billingRestTemplate(OutboundAuthRequestInterceptor outboundAuthRequestInterceptor) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setInterceptors(List.of(outboundAuthRequestInterceptor));
        return restTemplate;
    }
}

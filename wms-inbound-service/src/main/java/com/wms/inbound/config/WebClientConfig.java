package com.wms.inbound.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${wms.core-service.url:http://localhost:8081}")
    private String coreServiceUrl;

    @Value("${wms.integration-service.url:http://localhost:8085}")
    private String integrationServiceUrl;

    @Value("${wms.webclient.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${wms.webclient.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Value("${wms.webclient.write-timeout-ms:10000}")
    private int writeTimeoutMs;

    @Bean
    public WebClient coreWebClient() {
        return buildWebClient(coreServiceUrl);
    }

    @Bean
    public WebClient integrationWebClient() {
        return buildWebClient(integrationServiceUrl);
    }

    private WebClient buildWebClient(String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(readTimeoutMs))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

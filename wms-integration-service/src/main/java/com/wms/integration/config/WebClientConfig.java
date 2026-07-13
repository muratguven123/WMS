package com.wms.integration.config;

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

/**
 * ERP adaptörlerinin kullandığı paylaşık {@link WebClient} bean'i.
 *
 * <p>Bağlantı/okuma/yazma zaman aşımları {@code application.yml}'den
 * okunur. Üretim ortamında her ERP için ayrı WebClient bean'i
 * tanımlanabilir (farklı SSL, auth header vb.).
 */
@Configuration
public class WebClientConfig {

    @Value("${erp.webclient.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${erp.webclient.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Value("${erp.webclient.write-timeout-ms:10000}")
    private int writeTimeoutMs;

    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(readTimeoutMs))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

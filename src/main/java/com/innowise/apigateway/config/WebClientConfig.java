package com.innowise.apigateway.config;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient beans for downstream service communication.
 * URLs are bound from {@link ServicesProperties}; both clients share one
 * {@link ReactorClientHttpConnector} with connect-2s / response-5s timeouts.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ServicesProperties.class)
public class WebClientConfig {

    private final ServicesProperties services;
    private final ReactorClientHttpConnector connector = createTimeoutConnector();

    @Bean
    public WebClient authServiceWebClient(WebClient.Builder builder) {
        return buildServiceClient(builder, services.authServiceUrl());
    }

    @Bean
    public WebClient userServiceWebClient(WebClient.Builder builder) {
        return buildServiceClient(builder, services.userServiceUrl());
    }

    private WebClient buildServiceClient(WebClient.Builder builder, String baseUrl) {
        return builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(connector)
                .build();
    }

    private static ReactorClientHttpConnector createTimeoutConnector() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .responseTimeout(Duration.ofSeconds(5));
        return new ReactorClientHttpConnector(httpClient);
    }
}

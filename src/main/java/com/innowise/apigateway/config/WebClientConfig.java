package com.innowise.apigateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/** WebClient beans for downstream service communication. URLs bound from {@link ServicesProperties}. */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ServicesProperties.class)
public class WebClientConfig {

    private final ServicesProperties services;

    @Bean
    public WebClient authServiceWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(services.authServiceUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public WebClient userServiceWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(services.userServiceUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}

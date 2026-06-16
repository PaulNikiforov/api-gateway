package com.innowise.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Downstream service base URLs bound from the {@code services.*} configuration prefix. */
@ConfigurationProperties(prefix = "services")
public record ServicesProperties(String authServiceUrl, String userServiceUrl, String orderServiceUrl) {}

package com.innowise.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services")
public record ServicesProperties(String authServiceUrl, String userServiceUrl, String orderServiceUrl) {}

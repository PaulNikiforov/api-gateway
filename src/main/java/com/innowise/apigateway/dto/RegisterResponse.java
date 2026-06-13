package com.innowise.apigateway.dto;

/** Registration response: userId from User Service, token pair issued by Auth Service. */
public record RegisterResponse(Long userId, String accessToken, String refreshToken) {}
